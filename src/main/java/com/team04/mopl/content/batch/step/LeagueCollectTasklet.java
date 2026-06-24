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
 Step 1: 리그 ID 수집 Tasklet

 GET /all_leagues.php 호출 → 리그 ID 목록을 JobExecutionContext에 저장
 JobContext에 저장:
 - "leagueIds" (List<String>), 이때 직렬화되서 저장됨
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LeagueCollectTasklet implements Tasklet {

	private final SportsDbClient sportsDbClient;

	@Override
	public RepeatStatus execute(StepContribution contribution // 따로 사용 X
		, ChunkContext chunkContext) {
		log.info("[Batch] ===== Step 1: LeagueCollectTasklet 시작 =====");

		// Step 2(EventCollect)에서 사용할 리그 ID를 JobContext에 저장
		ExecutionContext jobContext = chunkContext.getStepContext()
			.getStepExecution()
			.getJobExecution()
			.getExecutionContext();

		// 리그 목록 조회 (10개)
		List<JsonNode> leagues = sportsDbClient.getAllLeagues();
		List<String> leagueIds = new ArrayList<>();

		// 리그 ID -> 리그명으로 변경 (깔끔한 로깅 처리)
		for (JsonNode league : leagues) {
			String leagueId = league.path("idLeague").asText();
			String leagueName = league.path("strLeague").asText();

			if (!StringUtils.hasText(leagueId)) {
				log.warn("[Batch] 리그 ID 누락, 건너뜀");
				continue;
			}

			leagueIds.add(leagueId);
			// Batch 메타데이터 테이블에 읽기 건수 기록 (BATCH_STEP_EXECUTION 테이블에서 확인 가능)
			contribution.incrementReadCount();
			log.debug("[Batch] 리그 수집: {} (id={})", leagueName, leagueId);
		}

		// jobContext에 leagueIds 저장
		jobContext.put("leagueIds", leagueIds);

		log.info("[Batch] ===== LeagueCollectTasklet 완료: 리그 {}건 =====", leagueIds.size());
		return RepeatStatus.FINISHED;
	}
}
