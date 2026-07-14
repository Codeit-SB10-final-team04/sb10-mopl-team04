package com.team04.mopl.playlist.batch;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.team04.mopl.conversation.repository.es.ConversationElasticSearchRepository;
import com.team04.mopl.playlist.repository.PlaylistRepository;

@SpringBootTest
@TestPropertySource(properties = {
	"spring.datasource.url=jdbc:h2:mem:playlist-hard-delete-batch-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
	"spring.jpa.hibernate.ddl-auto=none",
	"spring.sql.init.mode=always",
	"spring.sql.init.schema-locations=classpath:schema-h2-batch-test.sql",
	"spring.batch.jdbc.initialize-schema=always",
	"spring.batch.job.enabled=false"
})
public class PlaylistHardDeleteBatchTest {

	// ===================================================================
	// [ES Mocking 안내]
	// application-test.yml에서 ES 자동 설정을 제외했기 때문에,
	// 전체 컨텍스트 로드가 필요한 통합 테스트(@SpringBootTest)에서는
	// ES 관련 빈(Bean) 생성 실패(UnsatisfiedDependencyException)가 발생합니다.
	// 이를 방지하기 위해 컨텍스트 로드용 가짜 빈(MockBean)을 주입합니다.
	//
	// TODO: 향후 ES 실제 연동 테스트 필요 시 Testcontainers 환경으로 분리
	// ===================================================================

	@MockitoBean
	private ConversationElasticSearchRepository conversationElasticSearchRepository;

	@MockitoBean
	private ElasticsearchOperations elasticsearchOperations;

	@Autowired
	private JobLauncher jobLauncher;

	@Autowired
	private Job playlistHardDeleteJob;

	@Autowired
	private PlaylistRepository playlistRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	@DisplayName("플레이리스트 물리 삭제 배치는 기준일 이전에 논리 삭제된 플레이리스트만 삭제한다.")
	void playlistHardDeleteJob_deleteExpiredSoftDeletedPlaylists() throws Exception {
		// given
		LocalDate deleteDate = LocalDate.of(2026, 6, 1);

		UUID ownerId = UUID.randomUUID();
		UUID deletedPlaylistId1 = UUID.randomUUID();
		UUID deletedPlaylistId2 = UUID.randomUUID();
		UUID cutoffDeletedPlaylistId = UUID.randomUUID();
		UUID retentionPlaylistId = UUID.randomUUID();
		UUID activePlaylistId = UUID.randomUUID();

		insertUser(ownerId);
		insertPlaylist(ownerId, deletedPlaylistId1, "삭제1", Instant.parse("2026-04-01T00:00:00Z"));
		insertPlaylist(ownerId, deletedPlaylistId2, "삭제2", Instant.parse("2026-04-01T00:00:00Z"));
		insertPlaylist(ownerId, cutoffDeletedPlaylistId, "경계", Instant.parse("2026-05-31T15:00:00Z"));
		insertPlaylist(ownerId, retentionPlaylistId, "보관", Instant.parse("2026-06-02T00:00:00Z"));
		insertPlaylist(ownerId, activePlaylistId, "활성", null);

		JobParameters jobParameters = new JobParametersBuilder()
			.addLocalDate("deleteDate", deleteDate)
			.addLong("runId", System.currentTimeMillis())
			.toJobParameters();

		// when
		JobExecution jobExecution = jobLauncher.run(playlistHardDeleteJob, jobParameters);

		// then
		assertEquals(BatchStatus.COMPLETED, jobExecution.getStatus());
		assertFalse(playlistRepository.findById(deletedPlaylistId1).isPresent());
		assertFalse(playlistRepository.findById(deletedPlaylistId2).isPresent());
		assertTrue(playlistRepository.findById(cutoffDeletedPlaylistId).isPresent());
		assertTrue(playlistRepository.findById(retentionPlaylistId).isPresent());
		assertTrue(playlistRepository.findById(activePlaylistId).isPresent());
	}

	// h2가 sql의 enum 타입을 인식 못하는 문제로 JdbcTemplate을 사용해 직접 insert
	private void insertUser(UUID userId) {
		jdbcTemplate.update("""
				INSERT INTO users (
					id, name, email, email_type, role, is_locked, created_at, updated_at
				)
				VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
				""",
			userId,
			"테스트 사용자",
			"test@test.com",
			"REAL",
			"USER",
			false
		);
	}

	private void insertPlaylist(UUID ownerId, UUID playlistId, String title, Instant deletedAt) {
		jdbcTemplate.update("""
				INSERT INTO playlists (
					id, owner_id, title, description, created_at, updated_at, deleted_at
				)
				VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?)
				""",
			playlistId,
			ownerId,
			title,
			"테스트 설명",
			deletedAt != null ? Timestamp.from(deletedAt) : null
		);
	}
}
