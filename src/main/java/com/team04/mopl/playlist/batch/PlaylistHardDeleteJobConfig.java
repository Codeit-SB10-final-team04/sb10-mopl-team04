package com.team04.mopl.playlist.batch;

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
import com.team04.mopl.common.batch.metrics.MoplBatchJobMetricsListener;
import com.team04.mopl.common.batch.metrics.MoplBatchStepMetricsListener;
import com.team04.mopl.playlist.repository.PlaylistRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// Spring Batch Job/Step Bean 설정
@Configuration
@Slf4j
@RequiredArgsConstructor
public class PlaylistHardDeleteJobConfig {

	// Spring Batch 실행 정보를 저장하고 관리하는 저장소
	private final JobRepository jobRepository;

	// Step 실행 중 chunk 단위 트랜잭션을 관리하는 객체
	private final PlatformTransactionManager transactionManager;

	private final PlaylistRepository playlistRepository;

	private final MoplBatchJobMetricsListener moplBatchJobMetricsListener;
	private final MoplBatchStepMetricsListener moplBatchStepMetricsListener;

	// Job 객체를 Spring Bean으로 등록
	@Bean
	public Job playlistHardDeleteJob(Step playlistHardDeleteStep) {
		// `playlistHardDeleteJob` 이라는 Spring Batch Job을 생성
		return new JobBuilder("playlistHardDeleteJob", jobRepository)
			.listener(moplBatchJobMetricsListener)
			.start(playlistHardDeleteStep) // Job이 실행할 Step
			.build();
	}

	// Step 객체를 Spring Bean으로 등록
	@Bean
	public Step playlistHardDeleteStep(
		ItemReader<UUID> playlistHardDeleteReader,
		ItemWriter<UUID> playlistHardDeleteItemWriter
	) {
		// Step이 실행할 작업을 Chunk 단위로 Reader가 읽고 Writer가 삭제
		return new StepBuilder("playlistHardDeleteStep", jobRepository)
			.listener(moplBatchStepMetricsListener)
			.<UUID, UUID>chunk(100, transactionManager)
			.reader(playlistHardDeleteReader)
			.writer(playlistHardDeleteItemWriter)
			.build();
	}

	// Job Parameter를 기준으로 삭제 대상 플레이리스트 id를 커서 방식으로 순차 조회하는 Reader를 Spring Bean으로 등록
	@Bean
	@StepScope // Job 실행 시점에 deleteDate 파라미터를 주입받기 위해 Step Scope 적용
	public JdbcCursorItemReader<UUID> playlistHardDeleteReader(
		// Reader가 DB에 연결할 때 사용할 DataSource(SQL 실행에 사용)
		DataSource dataSource,
		// Job 실행 시 전달된 deleteDate 파라미터 주입
		@Value("#{jobParameters['deleteDate']}") LocalDate deleteDate
	) {
		// 한국 시간 기준 deleteDate를 물리 삭제 기준 시간으로 변환
		Instant deletedAtBefore = deleteDate.atStartOfDay(BatchTimeZone.KST).toInstant();

		// JDBC Cursor ItemReader Builder 생성
		return new JdbcCursorItemReaderBuilder<UUID>()
			// Reader 이름 지정
			.name("playlistHardDeleteReader")
			// SQL 실행에 사용할 DataSource 설정
			.dataSource(dataSource)
			// 기준 시간보다 먼저 논리 삭제된 플레이리스트 id만 조회
			.sql("""
					SELECT id
					FROM playlists
					WHERE deleted_at < ?
					ORDER BY deleted_at ASC, id ASC
				""")
			// SQL 첫 번째 바인딩 파라미터에 삭제 기준 시간(deletedAtBefore) 전달
			.preparedStatementSetter(ps ->
				ps.setTimestamp(1, Timestamp.from(deletedAtBefore))
			)
			// 조회한 id column을 UUID로 매핑 (rs:현재 row를 가리킴, rowNum: 현재 row의 번호)
			.rowMapper((rs, rowNum) -> rs.getObject("id", UUID.class))
			// DB에서 한 번에 가져갈 row 개수
			.fetchSize(100)
			// Reader 객체 생성
			.build();
	}

	// Reader가 조회한 플레이리스트 ID 목록을 물리 삭제하는 writer 등록
	@Bean
	public ItemWriter<UUID> playlistHardDeleteItemWriter() {
		// Chunk 단위로 전달받은 플레이리스트 id 목록을 처리
		return chunk -> {
			// 현재 Chunk에 포함된 플레이리스트 id 목록 추출
			List<UUID> playlistIds = new ArrayList<>(chunk.getItems());
			if (playlistIds.isEmpty()) {
				return;
			}

			// 해당 id의 플레이리스트를 DB에서 물리 삭제
			playlistRepository.deleteAllByPlaylistIds(playlistIds);

			log.info("[PLAYLIST_HARD_DELETE_BATCH] 플레이리스트 chunk 물리 삭제: count={}", playlistIds.size());
		};
	}
}
