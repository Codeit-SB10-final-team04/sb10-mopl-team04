package com.team04.mopl.content.batch.step;

import java.util.List;

import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.team04.mopl.content.client.SportsDbClient;
import com.team04.mopl.content.entity.Content;
import com.team04.mopl.content.entity.ContentTag;
import com.team04.mopl.content.entity.ContentType;
import com.team04.mopl.content.entity.Tag;
import com.team04.mopl.content.repository.ContentRepository;
import com.team04.mopl.content.repository.ContentTagRepository;
import com.team04.mopl.content.repository.TagRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 Step 2: 경기 수집 Tasklet

 Step 1에서 JobContext에 저장한 리그 ID 목록 load 후
 시즌별 경기 목록과 경기 상세 정보를 수집하고 Content 테이블에 저장 (ContentType.sport)

 API 호출 순서 (리그 1개당):
 1. GET /eventsseason.php?id={leagueId}&s={season} → 경기 ID 목록 (무료 최대 15건)
 2. GET /lookupevent.php?id={eventId}              → 경기 상세 저장

 저장 필드:
 - title      : strEvent (경기명)
 - description: strFilename (예: "FA Cup 2017-05-27 Arsenal vs Chelsea")
 - thumbnailUrl: strThumb → strLeagueBadge → 빈 문자열 순으로 fallback

 태그 저장 (없으면 신규 생성, 있으면 재사용):
 - "Sports"        : 고정 태그
 - strSport        : 종목 (예: "Soccer")
 - strVenue        : 홈구장 (예: "Wembley Stadium")
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchCollectTasklet implements Tasklet {

	private final SportsDbClient sportsDbClient;
	private final ContentRepository contentRepository;
	private final TagRepository tagRepository;
	private final ContentTagRepository contentTagRepository;

	// 수집할 시즌 (application.yml의 sports.batch.season으로 설정 가능)
	@Value("${sports.batch.season:2024-2025}")
	private String targetSeason;

	@Override
	@SuppressWarnings("unchecked") // Object[] -> List<String> cast 변환 경고 무시
	public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
		log.info("[Batch] ===== Step 2: MatchCollectTasklet 시작 (시즌: {}) =====", targetSeason);

		// chunkContext load
		ExecutionContext jobContext = chunkContext.getStepContext()
			.getStepExecution()
			.getJobExecution()
			.getExecutionContext();

		// 리그 id 목록 String으로 형변환
		List<String> leagueIds = (List<String>)jobContext.get("leagueIds");

		if (leagueIds == null || leagueIds.isEmpty()) {
			log.warn("[Batch] 리그 ID 목록 없음. LeagueCollectTasklet을 먼저 실행하세요.");
			return RepeatStatus.FINISHED;
		}

		int totalSaved = 0;
		int totalSkipped = 0;

		for (String leagueId : leagueIds) {
			// 리그별 시즌 경기 목록 조회 (무료 최대 15건)
			// GET /eventsseason.php?id={leagueId}&s={season}
			List<JsonNode> events = sportsDbClient.getEventsBySeason(leagueId, targetSeason);
			log.debug("[Batch] 경기 {}건 조회 (리그ID: {})", events.size(), leagueId);

			for (JsonNode event : events) {
				String eventId = event.path("idEvent").asText();
				String eventName = event.path("strEvent").asText();

				// 경기 정보 없을 경우 skip
				if (!StringUtils.hasText(eventId) || !StringUtils.hasText(eventName)) {
					log.warn("[Batch] 경기 필수 필드 누락, 건너뜀");
					totalSkipped++;
					continue;
				}

				// Todo: 중복 저장 방지 (경기명, Type 기준) -> QA 때 대응할 예정
				if (contentRepository.existsByTitleAndType(eventName, ContentType.sport)) {
					log.debug("[Batch] 경기 이미 존재, 건너뜀: {}", eventName);
					totalSkipped++;
					continue;
				}

				// 경기 상세 조회 (strFilename, strThumb, strSport, strVenue 포함)
				// GET /lookupevent.php?id={eventId}
				JsonNode eventDetail = sportsDbClient.getEventDetail(eventId).orElse(event);

				// description: strFilename 사용 (예: "FA Cup 2017-05-27 Arsenal vs Chelsea")
				String description = eventDetail.path("strFilename").asText("");

				// thumbnailUrl fallback: strThumb → strLeagueBadge → 빈 문자열
				// /small suffix: TheSportsDB 공식 리사이징 지원 (250px)
				String strThumb = eventDetail.path("strThumb").asText("");
				String rawUrl = StringUtils.hasText(strThumb)
					? strThumb
					: eventDetail.path("strLeagueBadge").asText("");
				String thumbnailUrl = StringUtils.hasText(rawUrl) ? rawUrl + "/small" : "";

				// Content 생성
				Content content = contentRepository.save(Content.builder()
					.title(eventDetail.path("strEvent").asText(eventName)) // eventDetail의 strEvent를 title로 설정
					.type(ContentType.sport) // ContentType.sport
					.description(description) // strFilename
					.thumbnailUrl(thumbnailUrl) // thumbnailUrl fallback
					.build());

				// 태그 저장: "Sports", strSport, strVenue
				saveTags(content, eventDetail);

				totalSaved++;
				// Batch 메타데이터 테이블에 쓰기 건수 기록 (BATCH_STEP_EXECUTION 테이블에서 확인 가능)
				contribution.incrementWriteCount(1);
				log.debug("[Batch] 경기 저장: {}", eventName);
			}
		}

		log.info("[Batch] ===== EventCollectTasklet 완료: 저장 {}건, 건너뜀 {}건 =====",
			totalSaved, totalSkipped);
		return RepeatStatus.FINISHED;
	}

	/**
	 경기 Content에 태그 연결
	 태그가 없으면 신규 생성, 있으면 기존 태그 재사용

	 저장 태그 목록:
	 - "Sports"   : 스포츠 콘텐츠 고정 태그
	 - strSport   : 종목 (예: "Soccer") , Soccer만 제공함
	 - strVenue   : 홈구장 (예: "Wembley Stadium"), 프로토타입 기준
	 */
	private void saveTags(Content content, JsonNode eventDetail) {
		// 고정 태그
		linkTag(content, "Sports");

		// 종목 태그 (예: Soccer, Basketball)
		String sport = eventDetail.path("strSport").asText("");
		if (StringUtils.hasText(sport)) {
			linkTag(content, sport);
		}

		// 홈구장 태그
		String venue = eventDetail.path("strVenue").asText("");
		if (StringUtils.hasText(venue)) {
			linkTag(content, venue);
		}
	}

	/**
	 태그명으로 Tag 조회 또는 생성 후 Content와 연결
	 */
	private void linkTag(Content content, String tagName) {
		// Tag 새로 생성 or 기존꺼 활용
		Tag tag = tagRepository.findByName(tagName)
			.orElseGet(() -> tagRepository.save(new Tag(tagName)));

		// contentTag 중간 테이블 저장
		contentTagRepository.save(ContentTag.builder()
			.content(content)
			.tag(tag)
			.build());
	}
}
