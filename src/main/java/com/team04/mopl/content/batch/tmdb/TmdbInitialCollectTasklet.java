package com.team04.mopl.content.batch.step;

import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.team04.mopl.content.client.TmdbClient;
import com.team04.mopl.content.entity.ContentType;
import com.team04.mopl.content.service.TmdbContentCollectService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 TMDB 초기 수집 Tasklet (최초 1회 실행)

 수집 대상:
 - movie/now_playing : 현재 상영 중인 영화 전체 페이지
 - tv/on_the_air    : 방영 중인 TV 시리즈 전체 페이지

 수집 흐름:
 1. page=1 호출 → total_pages 추출
 2. page=1 ~ total_pages 순차 호출 → Content/Tag 저장

 total_pages 상한: TMDB 정책상 최대 500 (500 × 20 = 10,000건)
 중복 체크는 TmdbContentCollectService.saveIfNotExists()에 위임
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TmdbInitialCollectTasklet implements Tasklet {

	// TMDB 정책상 최대 접근 가능 페이지 수
	private static final int MAX_PAGES = 500;

	private final TmdbClient tmdbClient;
	private final TmdbContentCollectService tmdbContentCollectService;

	@Override
	public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
		log.info("[TMDB] ===== 초기 수집 시작 =====");

		// movie/now_playing 전체 수집
		log.info("[TMDB] 상영 중 영화 수집 시작");
		JsonNode movieFirstPage = tmdbClient.getNowPlayingMovies(1);
		int movieTotalPages = Math.min(tmdbClient.extractTotalPages(movieFirstPage), MAX_PAGES);
		log.info("[TMDB] 총 페이지: {} (movie)", movieTotalPages);

		collectPage(movieFirstPage, ContentType.movie, contribution);
		for (int page = 2; page <= movieTotalPages; page++) {
			collectPage(tmdbClient.getNowPlayingMovies(page), ContentType.movie, contribution);
		}

		// tv/on_the_air 전체 수집
		log.info("[TMDB] 방영 중 TV 수집 시작");
		JsonNode tvFirstPage = tmdbClient.getOnAirTv(1);
		int tvTotalPages = Math.min(tmdbClient.extractTotalPages(tvFirstPage), MAX_PAGES);
		log.info("[TMDB] 총 페이지: {} (tv_series)", tvTotalPages);

		collectPage(tvFirstPage, ContentType.tvSeries, contribution);
		for (int page = 2; page <= tvTotalPages; page++) {
			collectPage(tmdbClient.getOnAirTv(page), ContentType.tvSeries, contribution);
		}

		log.info("[TMDB] ===== 초기 수집 완료: 저장 {}건 =====", contribution.getWriteCount());
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
