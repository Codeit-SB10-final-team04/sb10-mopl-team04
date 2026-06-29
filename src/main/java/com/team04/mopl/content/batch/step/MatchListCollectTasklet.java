package com.team04.mopl.content.batch.step;

import java.util.ArrayList;
import java.util.List;

import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.team04.mopl.content.client.SportsDbClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Step 2: 리그별 경기 ID 목록 수집
 * JobContext의 leagueIds를 읽어 시즌별 경기 ID 목록을 수집한 뒤
 * "eventIds" 키로 JobContext에 저장한다.
 *
 * <p>수집 시즌은 JobParameter "season"으로 전달받는다. (예: "2025-2026")
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MatchListCollectTasklet implements Tasklet {

	private static final String DEFAULT_SEASON = "2025-2026";

	private final SportsDbClient sportsDbClient;

	@Override
	@SuppressWarnings("unchecked")
	public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
		String season = chunkContext.getStepContext()
			.getStepExecution()
			.getJobExecution()
			.getJobParameters()
			.getString("season");

		if (season == null || season.isBlank()) {
			season = DEFAULT_SEASON;
		}

		log.info("[Batch] ===== Step 2: MatchListCollectTasklet 시작 (시즌: {}) =====", season);

		ExecutionContext jobContext = chunkContext.getStepContext()
			.getStepExecution()
			.getJobExecution()
			.getExecutionContext();

		List<String> leagueIds = (List<String>)jobContext.get("leagueIds");

		if (leagueIds == null || leagueIds.isEmpty()) {
			log.warn("[Batch] 리그 ID 목록 없음. LeagueCollectTasklet을 먼저 실행하세요.");
			jobContext.put("eventIds", new ArrayList<>());
			return RepeatStatus.FINISHED;
		}

		List<String> eventIds = new ArrayList<>();

		for (String leagueId : leagueIds) {
			List<JsonNode> events = sportsDbClient.getEventsBySeason(leagueId, season);
			log.debug("[Batch] 경기 {}건 조회 (리그ID: {})", events.size(), leagueId);

			for (JsonNode event : events) {
				String eventId = event.path("idEvent").asText();
				if (StringUtils.hasText(eventId)) {
					eventIds.add(eventId);
					contribution.incrementReadCount();
				}
			}
		}

		jobContext.put("eventIds", eventIds);
		log.info("[Batch] ===== MatchListCollectTasklet 완료: 경기 ID {}건 수집 =====", eventIds.size());
		return RepeatStatus.FINISHED;
	}
}
