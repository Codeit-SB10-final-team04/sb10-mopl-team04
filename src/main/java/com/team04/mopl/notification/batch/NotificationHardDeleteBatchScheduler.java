package com.team04.mopl.notification.batch;

import java.time.LocalDate;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.team04.mopl.common.batch.BatchTimeZone;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// 정해진 시간에 읽은 알림 삭제 요청
@Component
@Slf4j
@RequiredArgsConstructor
public class NotificationHardDeleteBatchScheduler {

	// 읽은 알림 보관 기간
	@Value("${notification.hard-delete.retention-months}")
	private long retentionMonths;

	private final NotificationHardDeleteBatchRunner notificationHardDeleteBatchRunner;

	@Scheduled(
		cron = "${notification.hard-delete.cron}",
		zone = BatchTimeZone.ZONE_ID
	)
	public void runNotificationHardDeleteBatch() {
		LocalDate deleteDate = LocalDate.now(BatchTimeZone.KST).minusMonths(retentionMonths);

		log.info("[NOTIFICATION_HARD_DELETE_BATCH] 알림 물리 삭제 스케줄 시작: deleteDate={}", deleteDate);

		notificationHardDeleteBatchRunner.run(deleteDate);

		log.info("[NOTIFICATION_HARD_DELETE_BATCH] 알림 물리 삭제 스케줄 완료: deleteDate={}", deleteDate);
	}
}
