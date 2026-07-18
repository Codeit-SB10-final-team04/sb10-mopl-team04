package com.team04.mopl.content.batch.tmdb;

import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.team04.mopl.content.client.TmdbClient;
import com.team04.mopl.content.entity.ContentType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class TmdbDailyCollectTasklet implements Tasklet {

	// 주기 수집 최대 페이지 수 (20건 × 10 = 200건)
	private static final int MAX_PAGES = 10;

	private final TmdbClient tmdbClient;
	private final TmdbContentCollectService tmdbContentCollectService;

	@Override
	public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
		log.info("[TMDB] ===== 주기 수집 시작 =====");

		// 장르 캐시 로드 (배치 시작 시 1회)
		tmdbContentCollectService.loadGenreCacheIfEmpty();

		// movie/upcoming 수집, 페이지 당 20건
		log.info("[TMDB] 개봉 예정 영화 수집 시작");
		for (int page = 1; page <= MAX_PAGES; page++) {
			collectPage(tmdbClient.getUpcomingMovies(page), ContentType.movie, contribution);
		}

		// tv/on_the_air 수집
		log.info("[TMDB] 방영 중 TV 수집 시작");
		for (int page = 1; page <= MAX_PAGES; page++) {
			collectPage(tmdbClient.getOnAirTv(page), ContentType.tvSeries, contribution);
		}

		log.info("[TMDB] ===== 주기 수집 완료: 저장 {}건 =====", contribution.getWriteCount());
		return RepeatStatus.FINISHED;
	}

	// results 순회 → saveIfNotExists → writeCount 증가 패턴을 공통 헬퍼로 추출
	private void collectPage(JsonNode root, ContentType type, StepContribution contribution) {
		for (JsonNode item : tmdbClient.extractResults(root)) {
			if (tmdbContentCollectService.saveIfNotExists(item, type)) {
				contribution.incrementWriteCount(1);
			}
		}
	}
}
