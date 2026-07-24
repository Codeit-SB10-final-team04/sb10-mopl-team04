package com.team04.mopl.playlist.integration;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.*;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team04.mopl.auth.security.MoplUserDetails;
import com.team04.mopl.content.entity.Content;
import com.team04.mopl.content.entity.ContentType;
import com.team04.mopl.content.repository.ContentRepository;
import com.team04.mopl.notification.kafka.NotificationKafkaTopics;
import com.team04.mopl.playlist.entity.Playlist;
import com.team04.mopl.playlist.entity.PlaylistSubscription;
import com.team04.mopl.playlist.event.PlaylistSubscribedEvent;
import com.team04.mopl.playlist.repository.PlaylistRepository;
import com.team04.mopl.playlist.repository.PlaylistSubscriptionRepository;
import com.team04.mopl.playlist.service.PlaylistContentService;
import com.team04.mopl.playlist.service.PlaylistSubscriptionService;
import com.team04.mopl.support.RealtimeIntegrationTestBase;
import com.team04.mopl.user.entity.User;
import com.team04.mopl.user.entity.UserRole;
import com.team04.mopl.user.repository.UserRepository;

public class PlaylistNotificationKafkaIntegrationTest extends RealtimeIntegrationTestBase {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private KafkaTemplate<String, String> kafkaTemplate;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PlaylistRepository playlistRepository;

	@Autowired
	private PlaylistSubscriptionRepository playlistSubscriptionRepository;

	@Autowired
	private ContentRepository contentRepository;

	@Autowired
	private PlaylistSubscriptionService playlistSubscriptionService;

	@Autowired
	private PlaylistContentService playlistContentService;

	@AfterEach
	void clearSecurityContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	@DisplayName("플레이리스트 구독 이벤트가 Kafka를 거쳐 소유자 알림으로 저장된다.")
	void subscribePlaylist_savesOwnerNotificationThroughKafka() {
		// given
		User owner = createUser("플레이리스트 소유자");
		User subscriber = createUser("구독자");
		Playlist playlist = createPlaylist(owner, "추천 영화");

		// when
		playlistSubscriptionService.subscribePlaylist(
			playlist.getId(),
			subscriber.getId()
		);

		// then
		await()
			.atMost(Duration.ofSeconds(15))
			.untilAsserted(() ->
				assertThat(countNotifications(owner.getId(), "SUBSCRIBE"))
					.isEqualTo(1L)
			);

		Map<String, Object> saved = jdbcTemplate.queryForMap("""
				SELECT title, content, type, source_event_id
				FROM notifications
				WHERE receiver_id = ?
					AND type = 'SUBSCRIBE'
				""",
			owner.getId()
		);

		assertThat(saved.get("title"))
			.isEqualTo("새 플레이리스트 구독 알림");
		assertThat(saved.get("content").toString())
			.contains(subscriber.getName())
			.contains(playlist.getTitle());
		assertThat(saved.get("source_event_id"))
			.isNotNull();
	}

	@Test
	@DisplayName("플레이리스트 콘텐츠 추가 이벤트가 구독자 알림으로 저장된다.")
	void addPlaylistContent_savesSubscriberNotificationThroughKafka() {
		// given
		User owner = createUser("콘텐츠 추가 소유자");
		User subscriber = createUser("콘텐츠 알림 구독자");
		Playlist playlist = createPlaylist(owner, "SF 영화 모음");
		Content content = createContent("인터스텔라");

		playlistSubscriptionRepository.saveAndFlush(
			PlaylistSubscription.builder()
				.subscriber(subscriber)
				.playlist(playlist)
				.build()
		);

		authenticate(owner);

		// when
		try {
			playlistContentService.addContentToPlaylist(
				playlist.getId(),
				content.getId(),
				owner.getId()
			);
		} finally {
			SecurityContextHolder.clearContext();
		}

		// then
		await()
			.atMost(Duration.ofSeconds(15))
			.untilAsserted(() ->
				assertThat(countNotifications(subscriber.getId(), "CONTENT_ADD"))
					.isEqualTo(1L)
			);

		Map<String, Object> saved = jdbcTemplate.queryForMap("""
				
				SELECT title, content, type, source_event_id
				FROM notifications
				WHERE receiver_id = ?
					AND type = 'CONTENT_ADD'
				""",
			subscriber.getId()
		);

		assertThat(saved.get("title"))
			.isEqualTo("새 콘텐츠 추가 알림");
		assertThat(saved.get("content").toString())
			.contains(playlist.getTitle())
			.contains(content.getTitle());
		assertThat(saved.get("source_event_id"))
			.isNotNull();
	}

	@Test
	@DisplayName("같은 Kafka 이벤트를 두 번 소비해도 한 건만 저장된다.")
	void duplicateKafkaEvent_savesOnlyOneNotification() throws Exception {
		// given
		User owner = createUser("중복 알림 수신자");
		UUID eventId = UUID.randomUUID();

		PlaylistSubscribedEvent event = new PlaylistSubscribedEvent(
			eventId,
			UUID.randomUUID(),
			"중복 테스트 플레이리스트",
			owner.getId(),
			UUID.randomUUID(),
			"중복 테스트 구독자",
			Instant.now()
		);

		String payload = objectMapper.writeValueAsString(event);

		// when
		kafkaTemplate.send(
			NotificationKafkaTopics.PLAYLIST_SUBSCRIBED,
			payload
		).get(10, TimeUnit.SECONDS);
		kafkaTemplate.send(
			NotificationKafkaTopics.PLAYLIST_SUBSCRIBED,
			payload
		).get(10, TimeUnit.SECONDS);

		// then
		await()
			.atMost(Duration.ofSeconds(15))
			.untilAsserted(() ->
				assertThat(countBySourceEvent(eventId, owner.getId()))
					.isEqualTo(1L)
			);
	}

	private long countNotifications(UUID receiverId, String type) {
		Long count = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM notifications
				WHERE receiver_id = ?
					AND type = ?
				""",
			Long.class,
			receiverId,
			type
		);

		return count == null ? 0L : count;
	}

	private long countBySourceEvent(UUID sourceEventId, UUID receiverId) {
		Long count = jdbcTemplate.queryForObject("""
				SELECT COUNT(*)
				FROM notifications
				WHERE source_event_id = ?
					AND receiver_id = ?
				""",
			Long.class,
			sourceEventId,
			receiverId
		);

		return count == null ? 0L : count;
	}

	private User createUser(String name) {
		UUID userId = UUID.randomUUID();

		jdbcTemplate.update("""
				INSERT INTO users (
					id,
					name,
					email,
					email_type,
					role,
					is_locked,
					created_at,
					updated_at
				)
				VALUES (?, ?, ?, 'REAL', 'USER', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
				""",
			userId,
			name,
			userId + "@test.com"
		);

		return userRepository.findById(userId).orElseThrow();
	}

	private Playlist createPlaylist(User owner, String title) {
		return playlistRepository.saveAndFlush(
			Playlist.builder()
				.owner(owner)
				.title(title)
				.description("Kafka 통합 테스트 플레이리스트")
				.build()
		);
	}

	private Content createContent(String title) {
		return contentRepository.saveAndFlush(
			Content.builder()
				.title(title)
				.type(ContentType.movie)
				.description("Kafka 통합 테스트 콘텐츠")
				.thumbnailUrl("https://test.local/thumbnail.png")
				.build()
		);
	}

	private void authenticate(User user) {
		MoplUserDetails principal = MoplUserDetails.authenticated(
			user.getId(),
			user.getEmail(),
			UserRole.USER
		);

		UsernamePasswordAuthenticationToken authentication =
			new UsernamePasswordAuthenticationToken(
				principal,
				null,
				principal.getAuthorities()
			);

		SecurityContextHolder.getContext().setAuthentication(authentication);
	}
}
