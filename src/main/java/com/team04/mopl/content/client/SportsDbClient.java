package com.team04.mopl.content.client;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.extern.slf4j.Slf4j;

/**
 TheSportsDB V1 Free API 클라이언트

 Base URL: https://www.thesportsdb.com/api/v1/json/123 (API Key "123" 경로 포함)
 Rate Limit: 무료 기준 30 req/min → 각 요청 후 2,000ms(1분) 대기
 429 응답 시 @Retryable로 60초 대기 후 재시도
 */
@Slf4j
@Component
public class SportsDbClient {

	private static final String BASE_URL = "https://www.thesportsdb.com/api/v1/json/123";

	// 분당 30번 제한 준수 → 2초마다 1번 호출
	private static final long REQUEST_DELAY_MS = 2_000L;

	private final RestClient restClient;

	public SportsDbClient() {
		// 타임아웃 설정 객체
		ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
			.withConnectTimeout(Duration.ofSeconds(10))  // 연결 타임아웃
			.withReadTimeout(Duration.ofSeconds(30));    // 응답 읽기 타임아웃

		// 설정을 factory에 적용!
		ClientHttpRequestFactory factory =
			ClientHttpRequestFactoryBuilder.detect().build(settings);

		// RestClient에 적용해서 build
		this.restClient = RestClient.builder()
			.requestFactory(factory)
			.baseUrl(BASE_URL)
			.build();
	}

	/**
	 전체 리그 목록 조회

	 엔드포인트: GET /all_leagues.php
	 무료 제한: 최대 10건

	 사용하는 필드:
	 - idLeague  : 리그 ID → EventCollectTasklet의 eventsseason 호출 파라미터
	 - strLeague : 리그 이름 (로깅용)

	 @return 리그 JsonNode 목록 (최대 10건, 응답 없으면 빈 리스트)
	 */
	@Retryable(
		retryFor = HttpClientErrorException.TooManyRequests.class,
		maxAttempts = 3,
		backoff = @Backoff(delay = 60_000)
	)
	public List<JsonNode> getAllLeagues() {
		log.info("[SportsDB] 전체 리그 목록 조회");

		JsonNode root = restClient.get()
			.uri("/all_leagues.php")
			.retrieve()
			.body(JsonNode.class);

		applyRateLimit();

		List<JsonNode> result = extractList(root, "leagues");
		log.info("[SportsDB] 리그 {}건 조회 완료", result.size());
		return result;
	}

	/**
	 시즌 경기 목록 조회

	 엔드포인트: GET /eventsseason.php?id={leagueId}&s={season}
	 무료 제한: 리그+시즌당 최대 15건

	 사용하는 필드:
	 - idEvent  : 경기 ID → getEventDetail 호출 파라미터
	 - strEvent : 경기명 (중복 체크용)

	 @param leagueId 리그 고유 ID
	 @param season   시즌명 (예: "2024-2025")
	 @return 경기 JsonNode 목록 (최대 15건, 응답 없으면 빈 리스트)
	 */
	@Retryable(
		retryFor = HttpClientErrorException.TooManyRequests.class,
		maxAttempts = 3,
		backoff = @Backoff(delay = 60_000)
	)
	public List<JsonNode> getEventsBySeason(String leagueId, String season) {
		log.debug("[SportsDB] 시즌 경기 목록 조회: leagueId={}, season={}", leagueId, season);

		JsonNode root = restClient.get()
			.uri("/eventsseason.php?id={id}&s={season}", leagueId, season)
			.retrieve()
			.body(JsonNode.class);

		applyRateLimit();

		return extractList(root, "events");
	}

	/**
	 경기 상세 조회

	 엔드포인트: GET /lookupevent.php?id={eventId}
	 무료 제한: 1건

	 사용하는 필드 (Content에 저장):
	 - strEvent         : 경기 제목 → Content.title
	 - strDescriptionEN : 경기 설명 → Content.description
	 - strThumb         : 경기 썸네일 URL → Content.thumbnailUrl 1순위 (유명 경기에만 존재)
	 - strLeagueBadge   : 리그 배지 URL → Content.thumbnailUrl 2순위 (strThumb null 시 fallback)

	 @param eventId 경기 고유 ID
	 @return 경기 상세 JsonNode (없으면 empty)
	 */
	@Retryable(
		retryFor = HttpClientErrorException.TooManyRequests.class,
		maxAttempts = 3,
		backoff = @Backoff(delay = 60_000)
	)
	public Optional<JsonNode> getEventDetail(String eventId) {
		log.debug("[SportsDB] 경기 상세 조회: eventId={}", eventId);

		JsonNode root = restClient.get()
			.uri("/lookupevent.php?id={id}", eventId)
			.retrieve()
			.body(JsonNode.class);

		applyRateLimit();

		return extractFirst(root, "events");
	}

	/**
	 {"leagues": [...]} 같은 응답에서 배열만 꺼내 List로 반환

	 @param root     API 응답 루트 JsonNode
	 @param arrayKey 배열 키 ("leagues", "teams", "events")
	 @return JsonNode 리스트 (없으면 빈 리스트)
	 */
	private List<JsonNode> extractList(JsonNode root, String arrayKey) {
		if (root == null || root.isMissingNode()) {
			return Collections.emptyList();
		}

		JsonNode array = root.path(arrayKey);
		if (array.isMissingNode() || !array.isArray()) {
			return Collections.emptyList();
		}

		List<JsonNode> result = new ArrayList<>();
		array.forEach(result::add);
		return result;
	}

	/**
	 단건 상세 조회 응답에서 첫 번째 요소만 추출

	 @param root     API 응답 루트 JsonNode
	 @param arrayKey 배열 키
	 @return 첫 번째 요소 Optional (없으면 empty)
	 */
	private Optional<JsonNode> extractFirst(JsonNode root, String arrayKey) {
		List<JsonNode> list = extractList(root, arrayKey);
		return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
	}

	/**
	 Rate Limit 준수: 요청마다 2초 대기 (분당 30건 제한이라 요청마다 2초 대기 적용)
	 InterruptedException 발생 시 스레드 인터럽트 상태 복원
	 */
	private void applyRateLimit() {
		try {
			Thread.sleep(REQUEST_DELAY_MS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			log.warn("[SportsDB] Rate limit 대기 중 인터럽트 발생");
		}
	}
}
