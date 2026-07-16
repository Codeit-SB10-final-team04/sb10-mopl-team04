package com.team04.mopl.content.client;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
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
 * TMDB API 클라이언트
 *
 * <p>Base URL: {@code https://api.themoviedb.org/3}
 *
 * <h3>인증</h3>
 * api_key 쿼리 파라미터 ({@code application.yml → tmdb.api.key})
 *
 * <h3>공통 응답 구조</h3>
 * <pre>{@code
 * {
 *   "page": 1,
 *   "total_pages": 500,      // TMDB 정책상 최대 500 고정
 *   "total_results": 43218,  // 실제 접근 가능 건수 최대 10,000건 (500 × 20)
 *   "results": [...]
 * }
 * }</pre>
 *
 * <h3>Movie vs TV 필드명 차이</h3>
 * <ul>
 *   <li>제목: {@code title} (Movie) / {@code name} (TV)</li>
 *   <li>설명: {@code overview} (공통) — 없는 경우 빈 문자열로 대응</li>
 *   <li>썸네일: {@code poster_path} (공통) → {@code https://image.tmdb.org/t/p/w500{poster_path}}</li>
 * </ul>
 *
 * <p>API 메서드는 루트 {@link com.fasterxml.jackson.databind.JsonNode}를 반환.
 * 호출부에서 {@code extractResults()}, {@code extractTotalPages()}로 분리 사용.
 *
 * <p>429 응답 시 {@code @Retryable}로 60초 대기 후 최대 3회 재시도.
 */
@Slf4j
@Component
public class TmdbClient {

	private static final String BASE_URL = "https://api.themoviedb.org/3";
	private static final String IMAGE_BASE_URL = "https://image.tmdb.org/t/p/w500";
	private static final String LANGUAGE = "ko-KR";

	private final String apiKey;
	private final RestClient restClient;

	public TmdbClient(@Value("${tmdb.api.key}") String apiKey) {
		this.apiKey = apiKey;

		ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.defaults()
			.withConnectTimeout(Duration.ofSeconds(10))
			.withReadTimeout(Duration.ofSeconds(30));

		ClientHttpRequestFactory factory =
			ClientHttpRequestFactoryBuilder.detect().build(settings);

		this.restClient = RestClient.builder()
			.requestFactory(factory)
			.baseUrl(BASE_URL)
			.build();
	}

	/**
	 현재 상영 중인 영화 목록 조회 (초기 수집용)

	 엔드포인트: GET /movie/now_playing?language=ko-KR&page={page}
	 사용 필드: id, title, overview, poster_path

	 @param page 페이지 번호 (1-based)
	 @return 응답 루트 JsonNode → extractResults(), extractTotalPages()로 파싱
	 */
	@Retryable(
		retryFor = HttpClientErrorException.TooManyRequests.class,
		maxAttempts = 3,
		backoff = @Backoff(delay = 60_000)
	)
	public JsonNode getNowPlayingMovies(int page) {
		log.debug("[TMDB] 상영 중 영화 조회: page={}", page);

		return restClient.get()
			.uri("/movie/now_playing?api_key={key}&language={lang}&page={page}",
				apiKey, LANGUAGE, page)
			.retrieve()
			.body(JsonNode.class);
	}

	/**
	 개봉 예정 영화 목록 조회 (주기 수집용)

	 엔드포인트: GET /movie/upcoming?language=ko-KR&page={page}
	 사용 필드: id, title, overview, poster_path

	 @param page 페이지 번호 (1-based)
	 @return 응답 루트 JsonNode → extractResults(), extractTotalPages()로 파싱
	 */
	@Retryable(
		retryFor = HttpClientErrorException.TooManyRequests.class,
		maxAttempts = 3,
		backoff = @Backoff(delay = 60_000)
	)
	public JsonNode getUpcomingMovies(int page) {
		log.debug("[TMDB] 개봉 예정 영화 조회: page={}", page);

		return restClient.get()
			.uri("/movie/upcoming?api_key={key}&language={lang}&page={page}",
				apiKey, LANGUAGE, page)
			.retrieve()
			.body(JsonNode.class);
	}

	/**
	 방영 중인 TV 시리즈 목록 조회 (초기 수집 + 주기 수집 공통)

	 엔드포인트: GET /tv/on_the_air?language=ko-KR&page={page}
	 사용 필드: id, name(→title), overview, poster_path
	 특이사항: 향후 7일 내 에피소드 방영 예정 시리즈 반환

	 @param page 페이지 번호 (1-based)
	 @return 응답 루트 JsonNode → extractResults(), extractTotalPages()로 파싱
	 */
	@Retryable(
		retryFor = HttpClientErrorException.TooManyRequests.class,
		maxAttempts = 3,
		backoff = @Backoff(delay = 60_000)
	)
	public JsonNode getOnAirTv(int page) {
		log.debug("[TMDB] 방영 중 TV 조회: page={}", page);

		return restClient.get()
			.uri("/tv/on_the_air?api_key={key}&language={lang}&page={page}",
				apiKey, LANGUAGE, page)
			.retrieve()
			.body(JsonNode.class);
	}

	/**
	 응답 루트에서 "results" 배열 추출

	 @param root API 응답 루트 JsonNode
	 @return JsonNode 리스트 (없으면 빈 리스트)
	 */
	public List<JsonNode> extractResults(JsonNode root) {
		if (root == null || root.isMissingNode()) {
			return Collections.emptyList();
		}

		JsonNode results = root.path("results");
		if (results.isMissingNode() || !results.isArray()) {
			return Collections.emptyList();
		}

		List<JsonNode> list = new ArrayList<>();
		results.forEach(list::add);
		return list;
	}

	/**
	 응답 루트에서 total_pages 추출

	 TMDB 정책상 total_pages 최대 500 고정
	 total_results가 10,000을 초과해도 500 이후 페이지는 접근 불가

	 @param root API 응답 루트 JsonNode
	 @return total_pages 값 (없으면 1)
	 */
	public int extractTotalPages(JsonNode root) {
		if (root == null || root.isMissingNode()) {
			return 1;
		}
		return root.path("total_pages").asInt(1);
	}

	/**
	 영화 장르 목록 조회

	 엔드포인트: GET /genre/movie/list?language=ko-KR
	 응답: {"genres": [{"id": 28, "name": "액션"}, ...]}

	 @return 장르 ID → 장르명 매핑 (예: {28: "액션", 878: "SF"})
	 */
	@Retryable(
		retryFor = HttpClientErrorException.TooManyRequests.class,
		maxAttempts = 3,
		backoff = @Backoff(delay = 60_000)
	)
	public Map<Integer, String> getMovieGenres() {
		log.debug("[TMDB] 영화 장르 목록 조회");

		JsonNode root = restClient.get()
			.uri("/genre/movie/list?api_key={key}&language={lang}", apiKey, LANGUAGE)
			.retrieve()
			.body(JsonNode.class);

		return parseGenres(root);
	}

	/**
	 TV 장르 목록 조회

	 엔드포인트: GET /genre/tv/list?language=ko-KR
	 응답: {"genres": [{"id": 10759, "name": "액션 & 어드벤처"}, ...]}

	 @return 장르 ID → 장르명 매핑
	 */
	@Retryable(
		retryFor = HttpClientErrorException.TooManyRequests.class,
		maxAttempts = 3,
		backoff = @Backoff(delay = 60_000)
	)
	public Map<Integer, String> getTvGenres() {
		log.debug("[TMDB] TV 장르 목록 조회");

		JsonNode root = restClient.get()
			.uri("/genre/tv/list?api_key={key}&language={lang}", apiKey, LANGUAGE)
			.retrieve()
			.body(JsonNode.class);

		return parseGenres(root);
	}

	/**
	 장르 응답 JSON → Map 변환

	 @param root API 응답 루트 JsonNode
	 @return 장르 ID → 장르명 매핑 (없으면 빈 맵)
	 */
	private Map<Integer, String> parseGenres(JsonNode root) {
		if (root == null || root.isMissingNode()) {
			return Map.of();
		}

		JsonNode genres = root.path("genres");
		if (genres.isMissingNode() || !genres.isArray()) {
			return Map.of();
		}

		Map<Integer, String> genreMap = new LinkedHashMap<>();
		for (JsonNode genre : genres) {
			int id = genre.path("id").asInt(0);
			String name = genre.path("name").asText("");
			if (id > 0 && !name.isBlank()) {
				genreMap.put(id, name);
			}
		}
		return genreMap;
	}

	/**
	 poster_path → 완성 썸네일 URL 조합

	 @param posterPath API 응답의 poster_path 값 (예: "/abc123.jpg")
	 @return 완성 URL (예: "https://image.tmdb.org/t/p/w500/abc123.jpg"), null이면 빈 문자열
	 */
	public String buildThumbnailUrl(String posterPath) {
		if (posterPath == null || posterPath.isBlank()) {
			return "";
		}
		return IMAGE_BASE_URL + posterPath;
	}
}
