package com.team04.mopl.watching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.team04.mopl.common.dto.CursorResponse;
import com.team04.mopl.common.enums.SortDirection;
import com.team04.mopl.content.entity.Content;
import com.team04.mopl.content.entity.ContentType;
import com.team04.mopl.content.exception.ContentException;
import com.team04.mopl.content.repository.ContentRepository;
import com.team04.mopl.user.entity.User;
import com.team04.mopl.user.exception.UserException;
import com.team04.mopl.user.repository.UserRepository;
import com.team04.mopl.watching.dto.request.WatchingSessionPageRequest;
import com.team04.mopl.watching.dto.response.WatchingSessionChange;
import com.team04.mopl.watching.dto.response.WatchingSessionDto;
import com.team04.mopl.watching.enums.ChangeType;
import com.team04.mopl.watching.enums.WatchingSessionSortBy;
import com.team04.mopl.watching.store.WatchingSessionStore;

@ExtendWith(MockitoExtension.class)
class WatchingSessionServiceTest {

	@Mock
	private WatchingSessionStore watchingSessionStore;

	@Mock
	private UserRepository userRepository;

	@Mock
	private ContentRepository contentRepository;

	@InjectMocks
	private WatchingSessionService watchingSessionService;

	@Test
	@DisplayName("시청 세션 입장 시 JOIN 이벤트를 반환한다")
	void join_returnsJoinChange_whenNewWatcher() {
		UUID contentId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		Instant joinedAt = Instant.now();

		when(watchingSessionStore.join("test-session", userId, contentId)).thenReturn(Optional.of(joinedAt));
		when(watchingSessionStore.getViewerCount(contentId)).thenReturn(1L);
		User user = mockUser(userId);
		when(userRepository.findByIdAndLockedFalse(userId)).thenReturn(Optional.of(user));
		Content content = mockContent(contentId);
		when(contentRepository.findById(contentId)).thenReturn(Optional.of(content));

		Optional<WatchingSessionChange> result = watchingSessionService.join(contentId, userId, "test-session");

		assertThat(result).isPresent();
		assertThat(result.get().type()).isEqualTo(ChangeType.JOIN);
		assertThat(result.get().watcherCount()).isEqualTo(1L);
	}

	@Test
	@DisplayName("이미 시청 중인 유저가 입장하면 empty를 반환한다")
	void join_returnsEmpty_whenAlreadyWatching() {
		UUID contentId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		User user = mockUser(userId);
		Content content = mockContent(contentId);

		when(userRepository.findByIdAndLockedFalse(userId)).thenReturn(Optional.of(user));
		when(contentRepository.findById(contentId)).thenReturn(Optional.of(content));
		when(watchingSessionStore.join("test-session", userId, contentId)).thenReturn(Optional.empty());

		Optional<WatchingSessionChange> result = watchingSessionService.join(contentId, userId, "test-session");

		assertThat(result).isEmpty();
	}

	@Test
	@DisplayName("시청 세션 퇴장 시 LEAVE 이벤트를 반환한다")
	void leave_returnsLeaveChange_whenWatching() {
		UUID contentId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		Instant joinedAt = Instant.now();

		when(watchingSessionStore.leave("test-session", userId, contentId)).thenReturn(Optional.of(joinedAt));
		when(watchingSessionStore.getViewerCount(contentId)).thenReturn(0L);
		User user = mockUser(userId);
		when(userRepository.findByIdAndLockedFalse(userId)).thenReturn(Optional.of(user));
		Content content = mockContent(contentId);
		when(contentRepository.findById(contentId)).thenReturn(Optional.of(content));

		Optional<WatchingSessionChange> result = watchingSessionService.leave(contentId, userId, "test-session");

		assertThat(result).isPresent();
		assertThat(result.get().type()).isEqualTo(ChangeType.LEAVE);
		assertThat(result.get().watcherCount()).isZero();
	}

	@Test
	@DisplayName("존재하지 않는 유저로 입장하면 UserException을 던진다")
	void join_throwsException_whenUserNotFound() {
		UUID contentId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();

		when(userRepository.findByIdAndLockedFalse(userId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> watchingSessionService.join(contentId, userId, "test-session"))
			.isInstanceOf(UserException.class);

		verify(watchingSessionStore, never()).join(any(), any(), any());
	}

	@Test
	@DisplayName("시청 중인지 확인할 수 있다")
	void isWatching_delegatesToStore() {
		UUID contentId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();

		when(watchingSessionStore.isViewing(contentId, userId)).thenReturn(true);

		assertThat(watchingSessionService.isWatching(contentId, userId)).isTrue();
	}

	@Test
	@DisplayName("시청 세션 목록이 createdAt 오름차순으로 정렬된다")
	void findByContentId_sortsByCreatedAtAscending() {
		UUID contentId = UUID.randomUUID();
		UUID user1 = UUID.randomUUID();
		UUID user2 = UUID.randomUUID();

		Instant earlier = Instant.parse("2026-07-06T10:00:00Z");
		Instant later = Instant.parse("2026-07-06T11:00:00Z");

		when(watchingSessionStore.getViewers(contentId)).thenReturn(Map.of(
			user1, later,
			user2, earlier
		));
		Content content = mockContent(contentId);
		when(contentRepository.findById(contentId)).thenReturn(Optional.of(content));
		List<User> users = List.of(mockUser(user1), mockUser(user2));
		when(userRepository.findAllByIdInAndLockedFalse(anySet())).thenReturn(users);

		CursorResponse<WatchingSessionDto> result =
			watchingSessionService.findByContentId(contentId, pageRequest(10, null, null));

		assertThat(result.data()).hasSize(2);
		assertThat(result.data().get(0).createdAt()).isEqualTo(earlier);
		assertThat(result.data().get(1).createdAt()).isEqualTo(later);
	}

	@Test
	@DisplayName("유저별 시청 세션 조회 시 가장 최근 입장 세션을 반환한다")
	void findByWatcherId_returnsLatestSession() {
		UUID watcherId = UUID.randomUUID();
		UUID oldContentId = UUID.randomUUID();
		UUID recentContentId = UUID.randomUUID();

		Instant recentTime = Instant.parse("2026-07-06T12:00:00Z");

		when(watchingSessionStore.getSessionsByUserId(watcherId)).thenReturn(Map.of(
			oldContentId, Instant.parse("2026-07-06T10:00:00Z"),
			recentContentId, recentTime
		));

		User user = mockUser(watcherId);
		when(userRepository.findByIdAndLockedFalse(watcherId)).thenReturn(Optional.of(user));
		Content content = mockContent(recentContentId);
		when(contentRepository.findById(recentContentId)).thenReturn(Optional.of(content));

		Optional<WatchingSessionDto> result = watchingSessionService.findByWatcherId(watcherId);

		assertThat(result).isPresent();
		assertThat(result.get().content().id()).isEqualTo(recentContentId);
		assertThat(result.get().createdAt()).isEqualTo(recentTime);
	}

	@Test
	@DisplayName("leaveBySessionId로 DISCONNECT 퇴장 처리")
	void leaveBySessionId_leavesSession() {
		String sessionId = "test-session";
		UUID userId = UUID.randomUUID();
		UUID contentId = UUID.randomUUID();
		Instant joinedAt = Instant.now();

		when(watchingSessionStore.getUserId(sessionId)).thenReturn(Optional.of(userId));
		when(watchingSessionStore.getContentId(sessionId)).thenReturn(Optional.of(contentId));
		when(watchingSessionStore.leave(sessionId, userId, contentId)).thenReturn(Optional.of(joinedAt));
		when(watchingSessionStore.getViewerCount(contentId)).thenReturn(0L);
		User user = mockUser(userId);
		when(userRepository.findByIdAndLockedFalse(userId)).thenReturn(Optional.of(user));
		Content content = mockContent(contentId);
		when(contentRepository.findById(contentId)).thenReturn(Optional.of(content));

		Optional<WatchingSessionChange> result = watchingSessionService.leaveBySessionId(sessionId);

		assertThat(result).isPresent();
		assertThat(result.get().type()).isEqualTo(ChangeType.LEAVE);
	}

	@Test
	@DisplayName("leaveBySessionId 시 매핑이 없으면 세션을 정리하고 empty를 반환한다")
	void leaveBySessionId_removesSession_whenMappingMissing() {
		String sessionId = "test-session";
		when(watchingSessionStore.getUserId(sessionId)).thenReturn(Optional.empty());
		when(watchingSessionStore.getContentId(sessionId)).thenReturn(Optional.empty());

		Optional<WatchingSessionChange> result = watchingSessionService.leaveBySessionId(sessionId);

		assertThat(result).isEmpty();
		verify(watchingSessionStore).removeSession(sessionId);
	}

	private WatchingSessionPageRequest pageRequest(int limit, String cursor, UUID idAfter) {
		return new WatchingSessionPageRequest(
			null, cursor, idAfter, limit,
			SortDirection.ASCENDING, WatchingSessionSortBy.createdAt
		);
	}

	private User mockUser(UUID userId) {
		User user = mock(User.class, withSettings().lenient());
		when(user.getId()).thenReturn(userId);
		when(user.getName()).thenReturn("user-" + userId.toString().substring(0, 4));
		when(user.getProfileImageUrl()).thenReturn(null);
		return user;
	}

	private Content mockContent(UUID contentId) {
		Content content = mock(Content.class, withSettings().lenient());
		when(content.getId()).thenReturn(contentId);
		when(content.getType()).thenReturn(ContentType.movie);
		when(content.getTitle()).thenReturn("test-content");
		when(content.getDescription()).thenReturn("test-description");
		when(content.getThumbnailUrl()).thenReturn("http://example.com/thumb.jpg");
		when(content.getAverageRating()).thenReturn(BigDecimal.ZERO);
		when(content.getReviewCount()).thenReturn(0L);
		return content;
	}
}
