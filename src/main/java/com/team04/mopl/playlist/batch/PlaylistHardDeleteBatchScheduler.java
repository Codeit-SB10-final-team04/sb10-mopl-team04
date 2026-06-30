package com.team04.mopl.playlist.batch;

import java.time.LocalDate;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.team04.mopl.common.batch.BatchTimeZone;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// 정해진 시간에 논리 삭제된 플레이리스트 삭제 요청
@Component
@Slf4j
@RequiredArgsConstructor
public class PlaylistHardDeleteBatchScheduler {

	// 논리 삭제한 플레이리스트 보관 기간
	@Value("${playlist.hard-delete.retention-months}")
	private long retentionMonths;

	private final PlaylistHardDeleteBatchRunner playlistHardDeleteBatchRunner;

	@Scheduled(
		cron = "${playlist.hard-delete.cron}",
		zone = BatchTimeZone.ZONE_ID
	)
	public void runPlaylistHardDeleteBatch() {
		LocalDate deleteDate = LocalDate.now(BatchTimeZone.KST).minusMonths(retentionMonths);

		log.info("[PLAYLIST_HARD_DELETE_BATCH] 플레이리스트 물리 삭제 스케줄 시작: deleteDate={}", deleteDate);

		playlistHardDeleteBatchRunner.run(deleteDate);

		log.info("[PLAYLIST_HARD_DELETE_BATCH] 플레이리스트 물리 삭제 스케줄 완료: deleteDate={}", deleteDate);
	}
}
