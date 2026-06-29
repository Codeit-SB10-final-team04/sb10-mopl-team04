package com.team04.mopl.playlist.repository.qdsl;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.*;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.team04.mopl.common.enums.SortDirection;
import com.team04.mopl.config.QuerydslConfig;
import com.team04.mopl.playlist.dto.request.PlaylistSearchRequest;
import com.team04.mopl.playlist.dto.response.PlaylistCursorPage;
import com.team04.mopl.playlist.enums.PlaylistSortBy;
import com.team04.mopl.playlist.exception.PlaylistErrorCode;
import com.team04.mopl.playlist.exception.PlaylistException;
import com.team04.mopl.playlist.repository.PlaylistRepository;

@DataJpaTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:playlist-querydsl-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
	"spring.jpa.hibernate.ddl-auto=none",
	"spring.sql.init.mode=always",
	"spring.sql.init.schema-locations=classpath:schema-h2-playlist_querydsl-test.sql"
})
@Import(QuerydslConfig.class)
class PlaylistQdslRepositoryImplTest {

	@Autowired
	private PlaylistRepository playlistRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	UUID owner1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
	UUID owner2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
	UUID subscriber1 = UUID.fromString("10000000-0000-0000-0000-000000000001");
	UUID subscriber2 = UUID.fromString("10000000-0000-0000-0000-000000000002");
	UUID subscriber3 = UUID.fromString("10000000-0000-0000-0000-000000000003");

	UUID playlist1 = UUID.fromString("20000000-0000-0000-0000-000000000001");
	UUID playlist2 = UUID.fromString("20000000-0000-0000-0000-000000000002");
	UUID playlist3 = UUID.fromString("20000000-0000-0000-0000-000000000003");
	UUID deleted_playlist = UUID.fromString("20000000-0000-0000-0000-000000000004");

	Instant updatedAt1 = Instant.parse("2026-06-24T03:00:00Z");
	Instant updatedAt2 = Instant.parse("2026-06-24T02:00:00Z");
	Instant updatedAt3 = Instant.parse("2026-06-24T01:00:00Z");

	@BeforeEach
	void setUp() {
		insertFixtures();
	}

	@Test
	@DisplayName("논리 삭제되지 않은 플레이리스트를 수정일 내림차순으로 조회한다.")
	void findPlaylists_returnCursorPageOrderByUpdatedAtDesc() {
		// given
		PlaylistSearchRequest request = new PlaylistSearchRequest(
			null, null, null, null, null,
			2,
			SortDirection.DESCENDING,
			PlaylistSortBy.updatedAt
		);

		// when
		PlaylistCursorPage result = playlistRepository.findPlaylists(request);

		// then
		assertEquals(2, result.playlistRows().size());
		assertThat(result.playlistRows())
			.extracting(row -> row.playlist().getId())
			.containsExactly(playlist1, playlist2);
		assertThat(result.playlistRows())
			.extracting(row -> row.subscriberCount())
			.containsExactly(2L, 1L);
		assertTrue(result.hasNext());
		assertEquals(3L, result.totalCount());
	}

	@Test
	@DisplayName("소유자 조건으로 플레이리스트를 조회한다.")
	void findPlaylists_returnCursorPage_whenOwnerIdEqualExists() {
		// given
		PlaylistSearchRequest request = new PlaylistSearchRequest(
			null, owner1, null, null, null,
			5,
			SortDirection.DESCENDING,
			PlaylistSortBy.updatedAt
		);

		// when
		PlaylistCursorPage result = playlistRepository.findPlaylists(request);

		// then
		assertThat(result.playlistRows())
			.extracting(row -> row.playlist().getId())
			.containsExactly(playlist1, playlist2);
		assertThat(result.playlistRows())
			.extracting(row -> row.ownerId())
			.containsOnly(owner1);
		assertFalse(result.hasNext());
		assertEquals(2L, result.totalCount());
	}

	@Test
	@DisplayName("구독자 조건으로 플레이리스트를 조회한다.")
	void findPlaylists_returnCursorPage_whenSubscriberIdEqualExists() {
		// given
		PlaylistSearchRequest request = new PlaylistSearchRequest(
			null, null, subscriber1, null, null,
			5,
			SortDirection.DESCENDING,
			PlaylistSortBy.updatedAt
		);

		// when
		PlaylistCursorPage result = playlistRepository.findPlaylists(request);

		// then
		assertThat(result.playlistRows())
			.extracting(row -> row.playlist().getId())
			.containsExactly(playlist1, playlist3);
		assertThat(result.playlistRows())
			.extracting(row -> row.subscriberCount())
			.containsExactly(2L, 3L);
		assertEquals(2L, result.totalCount());
	}

	@Test
	@DisplayName("updatedAt 커서가 있으면 이후 데이터를 조회한다.")
	void findPlaylists_returnCursorPage_whenAfterUpdatedAtCursor() {
		// given
		PlaylistSearchRequest request = new PlaylistSearchRequest(
			null, null, subscriber1, updatedAt2.toString(), playlist2,
			5,
			SortDirection.DESCENDING,
			PlaylistSortBy.updatedAt
		);

		// when
		PlaylistCursorPage result = playlistRepository.findPlaylists(request);

		// then
		assertThat(result.playlistRows())
			.extracting(row -> row.playlist().getId())
			.containsExactly(playlist3);
		assertFalse(result.hasNext());
		assertEquals(2L, result.totalCount());
	}

	@Test
	@DisplayName("subscriberCount 커서가 있으면 커서 이후 데이터를 조회한다.")
	void findPlaylists_returnCursorPage_whenAfterSubscriberCountCursor() {
		// given
		PlaylistSearchRequest request = new PlaylistSearchRequest(
			null, null, null, "2", playlist1,
			5,
			SortDirection.DESCENDING,
			PlaylistSortBy.subscribeCount
		);

		// when
		PlaylistCursorPage result = playlistRepository.findPlaylists(request);

		// then
		assertThat(result.playlistRows())
			.extracting(row -> row.playlist().getId())
			.containsExactly(playlist2);
		assertThat(result.playlistRows())
			.extracting(row -> row.subscriberCount())
			.containsExactly(1L);
		assertEquals(3L, result.totalCount());
	}

	@Test
	@DisplayName("cursor와 idAfter 중 하나만 있으면 예외가 발생한다.")
	void findPlaylists_throwException_whenOnlyCursorOrIdAfterProvided() {
		// given
		PlaylistSearchRequest request = new PlaylistSearchRequest(
			null, null, null, updatedAt2.toString(), null,
			5,
			SortDirection.DESCENDING,
			PlaylistSortBy.updatedAt
		);

		Throwable throwable = catchThrowable(() ->
			playlistRepository.findPlaylists(request)
		);
		PlaylistException playlistException = (PlaylistException)throwable;

		assertThat(throwable).isInstanceOf(PlaylistException.class);
		assertEquals(PlaylistErrorCode.INVALID_INPUT, playlistException.getErrorCode());
	}

	private void insertFixtures() {
		insertUser(owner1, "owner1");
		insertUser(owner2, "owner2");
		insertUser(subscriber1, "subscriber1");
		insertUser(subscriber2, "subscriber2");
		insertUser(subscriber3, "subscriber3");

		insertPlaylist(playlist1, owner1, "영화", "영화 설명", updatedAt1, null);
		insertPlaylist(playlist2, owner1, "드라마", "드라마 설명", updatedAt2, null);
		insertPlaylist(playlist3, owner2, "운동", "월드컵", updatedAt3, null);
		insertPlaylist(deleted_playlist, owner1, "삭제됨", "조회X", updatedAt1, updatedAt1);

		insertSubscription(subscriber1, playlist1);
		insertSubscription(subscriber2, playlist1);
		insertSubscription(subscriber2, playlist2);
		insertSubscription(subscriber1, playlist3);
		insertSubscription(subscriber2, playlist3);
		insertSubscription(subscriber3, playlist3);
		insertSubscription(subscriber1, deleted_playlist);
	}

	private void insertUser(UUID userId, String name) {
		jdbcTemplate.update("""
				INSERT INTO users (
					id, name, email, email_type, password_hash, profile_image_url,
				    role, is_locked, created_at, updated_at
				)
				VALUES (?, ?, ?, 'REAL', NULL, ?, 'USER', FALSE, ?, ?)
				""",
			userId,
			name,
			name + "@test.com",
			"https://example.com/" + name,
			Timestamp.from(Instant.parse("2026-06-24T00:00:00Z")),
			Timestamp.from(Instant.parse("2026-06-24T00:00:00Z"))
		);
	}

	private void insertPlaylist(
		UUID playlistId,
		UUID ownerId,
		String title,
		String description,
		Instant updatedAt,
		Instant deletedAt
	) {
		jdbcTemplate.update("""
				INSERT INTO playlists (
				    id, owner_id, title, description, created_at, updated_at, deleted_at
				)
				VALUES (?, ?, ?, ?, ?, ?, ?)
				""",
			playlistId,
			ownerId,
			title,
			description,
			Timestamp.from(Instant.parse("2026-06-24T00:00:00Z")),
			Timestamp.from(updatedAt),
			deletedAt == null ? null : Timestamp.from(deletedAt)
		);
	}

	private void insertSubscription(UUID subscriberId, UUID playlistId) {
		jdbcTemplate.update("""
				INSERT INTO playlist_subscriptions (
				    id, subscriber_id, playlist_id, created_at
				)
				VALUES (?, ?, ?, ?)
				""",
			UUID.randomUUID(),
			subscriberId,
			playlistId,
			Timestamp.from(Instant.parse("2026-06-24T00:00:00Z"))
		);
	}
}