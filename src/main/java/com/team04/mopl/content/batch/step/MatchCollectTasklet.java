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
import com.team04.mopl.content.service.MatchCollectService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class MatchCollectTasklet implements Tasklet {

	private final SportsDbClient sportsDbClient;
	private final MatchCollectService matchCollectService;

	// 수집할 시즌 (application.yml의 sports.batch.season으로 설정 가능)
	@Value("${sports.batch.season:2024-2025}")
	private String targetSeason;

	@Override
	@SuppressWarnings("unchecked") // Object → List<String> 캐스팅 경고 억제
	public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
		log.info("[Batch] ===== Step 2: MatchCollectTasklet 시작 (시즌: {}) =====", targetSeason);

		ExecutionContext jobContext = chunkContext.getStepContext()
			.getStepExecution()
			.getJobExecution()
			.getExecutionContext();

		List<String> leagueIds = (List<String>)jobContext.get("leagueIds");

		if (leagueIds == null || leagueIds.isEmpty()) {
			log.warn("[Batch] 리그 ID 목록 없음. LeagueCollectTasklet을 먼저 실행하세요.");
			return RepeatStatus.FINISHED;
		}

		int totalSaved = 0;
		int totalSkipped = 0;

		for (String leagueId : leagueIds) {
			// 리그별 시즌 경기 목록 조회 (무료 최대 15건)
			List<JsonNode> events = sportsDbClient.getEventsBySeason(leagueId, targetSeason);
			log.debug("[Batch] 경기 {}건 조회 (리그ID: {})", events.size(), leagueId);

			for (JsonNode event : events) {
				String eventId = event.path("idEvent").asText();
				String eventName = event.path("strEvent").asText();

				// 필수 필드 누락 시 skip
				if (!StringUtils.hasText(eventId) || !StringUtils.hasText(eventName)) {
					log.warn("[Batch] 경기 필수 필드 누락, 건너뜀");
					totalSkipped++;
					continue;
				}

				// 경기 상세 조회
				JsonNode eventDetail = sportsDbClient.getEventDetail(eventId).orElse(event);

				// 저장 (중복 체크, Content/Tag 저장은 Service 담당)
				boolean saved = matchCollectService.saveIfNotExists(eventDetail, eventName);

				if (saved) {
					totalSaved++;
					contribution.incrementWriteCount(1);
					log.debug("[Batch] 경기 저장: {}", eventName);
				} else {
					totalSkipped++;
				}
			}
		}

		log.info("[Batch] ===== MatchCollectTasklet 완료: 저장 {}건, 건너뜀 {}건 =====",
			totalSaved, totalSkipped);
		return RepeatStatus.FINISHED;
	}
}
