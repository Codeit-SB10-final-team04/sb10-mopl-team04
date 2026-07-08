package com.team04.mopl.notification.batch;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.database.JdbcCursorItemReader;
import org.springframework.batch.item.database.builder.JdbcCursorItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import com.team04.mopl.common.batch.BatchTimeZone;
import com.team04.mopl.notification.repository.NotificationRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// Spring Batch Job/Step Bean 설정
@Configuration
@Slf4j
@RequiredArgsConstructor
public class NotificationHardDeleteJobConfig {

	// Spring Batch 실행 정보를 저장하고 관리하는 저장소
	private final JobRepository jobRepository;

	// Step 실행 중 chunk 단위 트랜잭션을 관리하는 객체
	private final PlatformTransactionManager transactionManager;

	private final NotificationRepository notificationRepository;

	// Job 객체를 Spring Bean으로 등록
	@Bean
	public Job notificationHardDeleteJob(Step notificationHardDeleteStep) {
		// `notificationHardDeleteJob` 이라는 Spring Batch Job을 생성
		return new JobBuilder("notificationHardDeleteJob", jobRepository)
			.start(notificationHardDeleteStep) // Job이 실행할 step
			.build();
	}

	// Step 객체를 Spring Bean으로 등록
	@Bean
	public Step notificationHardDeleteStep(
		ItemReader<UUID> notificationHardDeleteItemReader,
		ItemWriter<UUID> notificationHardDeleteItemWriter
	) {
		// step이 실행할 작업을 chunk 단위로 Reader가 읽고 writer가 삭제
		return new StepBuilder("notificationHardDeleteStep", jobRepository)
			.<UUID, UUID>chunk(100, transactionManager)
			.reader(notificationHardDeleteItemReader)
			.writer(notificationHardDeleteItemWriter)
			.build();
	}

	// Job parameter 기준으로 삭제 대상 알림 id를 커서 방식으로 순차 조회하는 Reader를 Spring Bean으로 등록
	@Bean
	@StepScope
	public JdbcCursorItemReader<UUID> notificationHardDeleteItemReader(
		// Reader가 DB에 연결할 때 사용할 DataSource(SQL 실행에 사용)
		DataSource dataSource,
		// Job 실행 시 전달될 delete 파라미터 주입
		@Value("#{jobParameters['deleteDate']}") LocalDate deleteDate
	) {
		// 한국 시간 기준 deleteDate를 물리 삭제 기준 시간으로 변환
		Instant deletedAtBefore = deleteDate.atStartOfDay(BatchTimeZone.KST).toInstant();

		// JDBC Cursor ItemReader Builder 생성
		return new JdbcCursorItemReaderBuilder<UUID>()
			// Reader 이름 지정
			.name("notificationHardDeleteItemReader")
			// SQL 실행에 사용할 DataSource 설정
			.dataSource(dataSource)
			// 기준 시간보다 먼저 읽은 notification id만 조회
			.sql("""
				SELECT id
				FROM notifications
				WHERE read_at < ?
				ORDER BY read_at ASC, id ASC
				""")
			// SQL 첫 번째 바인딩 파라미터에 삭제 기준 시간(deletedAtBefore) 전달
			.preparedStatementSetter(ps ->
				ps.setTimestamp(1, Timestamp.from(deletedAtBefore))
			)
			// 조회한 id column을 UUID로 매핑 (rs: 현재 row, rowNum: 현재 row 번호)
			.rowMapper((rs, rowNum) -> rs.getObject("id", UUID.class))
			// DB에서 한 번에 가져갈 row 개수
			.fetchSize(100)
			// Reader 객체 생성
			.build();
	}

	// Reader가 조회한 알림 목록을 물리 삭제하는 writer 등록
	@Bean
	public ItemWriter<UUID> notificationHardDeleteItemWriter() {
		// chunk 단위로 전달받은 알림 id 목록 처리
		return chunk -> {
			// 현재 chunk에 포함된 알림 id 목록 추출
			List<UUID> notificationIds = new ArrayList<>(chunk.getItems());
			if (notificationIds.isEmpty()) {
				return;
			}

			// 해당 id의 알림을 DB에서 물리 삭제
			notificationRepository.deleteAllByNotificationIds(notificationIds);

			log.info("[NOTIFICATION_HARD_DELETE_BATCH] 알림 chunk 물리 삭제: count={}", notificationIds.size());
		};
	}
}
