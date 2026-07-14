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
import com.team04.mopl.watching.store.WatchingSessionInfo;
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
		// given
		UUID contentId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		WatchingSessionInfo info = new WatchingSessionInfo(UUID.randomUUID(), Instant.now());

		when(watchingSessionStore.addWatcher(contentId, userId, "test-session")).thenReturn(Optional.of(info));
		when(watchingSessionStore.getWatcherCount(contentId)).thenReturn(1L);
		User user = mockUser(userId);
		when(userRepository.findByIdAndLockedFalse(userId)).thenReturn(Optional.of(user));
		Content content = mockContent(contentId);
		when(contentRepository.findById(contentId)).thenReturn(Optional.of(content));

		// when
		Optional<WatchingSessionChange> result = watchingSessionService.join(contentId, userId, "test-session");

		// then
		assertThat(result).isPresent();
		assertThat(result.get().type()).isEqualTo(ChangeType.JOIN);
		assertThat(result.get().watcherCount()).isEqualTo(1L);
		// Store에 저장된 세션 정보(id/joinedAt)가 그대로 사용되는지 검증
		assertThat(result.get().watchingSession().id()).isEqualTo(info.id());
		assertThat(result.get().watchingSession().createdAt()).isEqualTo(info.joinedAt());
	}

	@Test
	@DisplayName("이미 시청 중인 유저가 입장하면 empty를 반환한다")
	void join_returnsEmpty_whenAlreadyWatching() {
		// given
		UUID contentId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();

		// 검증이 먼저 수행되므로 user/content 조회는 통과시킴
		User user = mockUser(userId);
		when(userRepository.findByIdAndLockedFalse(userId)).thenReturn(Optional.of(user));
		Content content = mockContent(contentId);
		when(contentRepository.findById(contentId)).thenReturn(Optional.of(content));

		when(watchingSessionStore.addWatcher(contentId, userId, "test-session")).thenReturn(Optional.empty());

		// when
		Optional<WatchingSessionChange> result = watchingSessionService.join(contentId, userId, "test-session");

		// then
		assertThat(result).isEmpty();
	}

	@Test
	@DisplayName("시청 세션 퇴장 시 LEAVE 이벤트를 반환한다")
	void leave_returnsLeaveChange_whenWatching() {
		// given
		UUID contentId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();
		WatchingSessionInfo info = new WatchingSessionInfo(UUID.randomUUID(), Instant.now());

		when(watchingSessionStore.removeWatcher(contentId, userId, "test-session")).thenReturn(Optional.of(info));
		when(watchingSessionStore.getWatcherCount(contentId)).thenReturn(0L);
		User user = mockUser(userId);
		when(userRepository.findByIdAndLockedFalse(userId)).thenReturn(Optional.of(user));
		Content content = mockContent(contentId);
		when(contentRepository.findById(contentId)).thenReturn(Optional.of(content));

		// when
		Optional<WatchingSessionChange> result = watchingSessionService.leave(contentId, userId, "test-session");

		// then
		assertThat(result).isPresent();
		assertThat(result.get().type()).isEqualTo(ChangeType.LEAVE);
		assertThat(result.get().watcherCount()).isZero();
	}

	@Test
	@DisplayName("시청 중이 아닌 유저가 퇴장하면 empty를 반환한다")
	void leave_returnsEmpty_whenNotWatching() {
		// given
		UUID contentId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();

		// 검증이 먼저 수행되므로 user/content 조회는 통과시킴
		User user = mockUser(userId);
		when(userRepository.findByIdAndLockedFalse(userId)).thenReturn(Optional.of(user));
		Content content = mockContent(contentId);
		when(contentRepository.findById(contentId)).thenReturn(Optional.of(content));

		when(watchingSessionStore.removeWatcher(contentId, userId, "test-session")).thenReturn(Optional.empty());

		// when
		Optional<WatchingSessionChange> result = watchingSessionService.leave(contentId, userId, "test-session");

		// then
		assertThat(result).isEmpty();
	}

	@Test
	@DisplayName("존재하지 않는 유저로 입장하면 UserException을 던지고 Store는 변경되지 않는다")
	void join_throwsException_whenUserNotFound() {
		// given
		UUID contentId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();

		when(userRepository.findByIdAndLockedFalse(userId)).thenReturn(Optional.empty());

		// when & then
		assertThatThrownBy(() -> watchingSessionService.join(contentId, userId, "test-session"))
			.isInstanceOf(UserException.class);

		// 검증 실패 시 Store에 좀비 시청자가 추가되지 않아야 함
		verify(watchingSessionStore, never()).addWatcher(any(), any());
	}

	@Test
	@DisplayName("존재하지 않는 콘텐츠로 목록 조회하면 ContentException을 던진다")
	void findByContentId_throwsException_whenContentNotFound() {
		// given
		UUID contentId = UUID.randomUUID();

		when(watchingSessionStore.getWatchers(contentId)).thenReturn(Map.of());
		when(contentRepository.findById(contentId)).thenReturn(Optional.empty());

		// when & then
		assertThatThrownBy(() -> watchingSessionService.findByContentId(contentId, pageRequest(10, null, null)))
			.isInstanceOf(ContentException.class);
	}

	@Test
	@DisplayName("시청 중인지 확인할 수 있다")
	void isWatching_delegatesToStore() {
		// given
		UUID contentId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();

		when(watchingSessionStore.isWatching(contentId, userId)).thenReturn(true);

		// when & then
		assertThat(watchingSessionService.isWatching(contentId, userId)).isTrue();
	}

	@Test
	@DisplayName("시청 세션 목록이 createdAt 오름차순으로 정렬된다")
	void findByContentId_sortsByCreatedAtAscending() {
		// given
		UUID contentId = UUID.randomUUID();
		UUID user1 = UUID.randomUUID();
		UUID user2 = UUID.randomUUID();

		Instant earlier = Instant.parse("2026-07-06T10:00:00Z");
		Instant later = Instant.parse("2026-07-06T11:00:00Z");

		when(watchingSessionStore.getWatchers(contentId)).thenReturn(Map.of(
			user1, new WatchingSessionInfo(UUID.randomUUID(), later),
			user2, new WatchingSessionInfo(UUID.randomUUID(), earlier)
		));
		Content content = mockContent(contentId);
		when(contentRepository.findById(contentId)).thenReturn(Optional.of(content));
		List<User> users = List.of(mockUser(user1), mockUser(user2));
		when(userRepository.findAllByIdInAndLockedFalse(anySet())).thenReturn(users);

		// when
		CursorResponse<WatchingSessionDto> result =
			watchingSessionService.findByContentId(contentId, pageRequest(10, null, null));

		// then
		assertThat(result.data()).hasSize(2);
		assertThat(result.data().get(0).createdAt()).isEqualTo(earlier);
		assertThat(result.data().get(1).createdAt()).isEqualTo(later);
	}

	@Test
	@DisplayName("커서 페이지네이션 연속 호출 시 다음 페이지가 정상 반환된다")
	void findByContentId_paginationContinues_withStableIds() {
		// given: 시청자 3명, 페이지 크기 2
		UUID contentId = UUID.randomUUID();
		UUID user1 = UUID.randomUUID();
		UUID user2 = UUID.randomUUID();
		UUID user3 = UUID.randomUUID();

		Map<UUID, WatchingSessionInfo> watchers = Map.of(
			user1, new WatchingSessionInfo(UUID.randomUUID(), Instant.parse("2026-07-06T10:00:00Z")),
			user2, new WatchingSessionInfo(UUID.randomUUID(), Instant.parse("2026-07-06T11:00:00Z")),
			user3, new WatchingSessionInfo(UUID.randomUUID(), Instant.parse("2026-07-06T12:00:00Z"))
		);

		when(watchingSessionStore.getWatchers(contentId)).thenReturn(watchers);
		Content content = mockContent(contentId);
		when(contentRepository.findById(contentId)).thenReturn(Optional.of(content));
		List<User> users = List.of(mockUser(user1), mockUser(user2), mockUser(user3));
		when(userRepository.findAllByIdInAndLockedFalse(anySet())).thenReturn(users);

		// when: 1페이지 조회
		CursorResponse<WatchingSessionDto> firstPage =
			watchingSessionService.findByContentId(contentId, pageRequest(2, null, null));

		// then: 2건 + hasNext
		assertThat(firstPage.data()).hasSize(2);
		assertThat(firstPage.hasNext()).isTrue();
		assertThat(firstPage.nextCursor()).isNotNull();
		assertThat(firstPage.nextIdAfter()).isNotNull();

		// when: 1페이지의 커서로 2페이지 조회 (Store의 id/joinedAt이 안정적이므로 이어서 조회 가능)
		CursorResponse<WatchingSessionDto> secondPage = watchingSessionService.findByContentId(
			contentId,
			pageRequest(2, firstPage.nextCursor(), UUID.fromString(firstPage.nextIdAfter()))
		);

		// then: 남은 1건 반환, 1페이지와 중복 없음
		assertThat(secondPage.data()).hasSize(1);
		assertThat(secondPage.hasNext()).isFalse();
		assertThat(secondPage.data().get(0).id())
			.isNotIn(firstPage.data().get(0).id(), firstPage.data().get(1).id());
	}

	@Test
	@DisplayName("유저별 시청 세션 조회 시 가장 최근 입장 세션을 반환한다")
	void findByWatcherId_returnsLatestSession() {
		// given
		UUID watcherId = UUID.randomUUID();
		UUID oldContentId = UUID.randomUUID();
		UUID recentContentId = UUID.randomUUID();

		WatchingSessionInfo recentInfo =
			new WatchingSessionInfo(UUID.randomUUID(), Instant.parse("2026-07-06T12:00:00Z"));

		when(watchingSessionStore.getWatchingSessions(watcherId)).thenReturn(Map.of(
			oldContentId, new WatchingSessionInfo(UUID.randomUUID(), Instant.parse("2026-07-06T10:00:00Z")),
			recentContentId, recentInfo
		));
		User watcher = mockUser(watcherId);
		when(userRepository.findByIdAndLockedFalse(watcherId)).thenReturn(Optional.of(watcher));
		Content recentContent = mockContent(recentContentId);
		when(contentRepository.findById(recentContentId)).thenReturn(Optional.of(recentContent));

		// when
		Optional<WatchingSessionDto> result = watchingSessionService.findByWatcherId(watcherId);

		// then
		assertThat(result).isPresent();
		assertThat(result.get().id()).isEqualTo(recentInfo.id());
		assertThat(result.get().content().id()).isEqualTo(recentContentId);
	}

	@Test
	@DisplayName("시청 중이 아닌 유저 조회 시 empty를 반환한다")
	void findByWatcherId_returnsEmpty_whenNotWatching() {
		// given
		UUID watcherId = UUID.randomUUID();

		when(watchingSessionStore.getWatchingSessions(watcherId)).thenReturn(Map.of());

		// when & then
		assertThat(watchingSessionService.findByWatcherId(watcherId)).isEmpty();
	}

	private User mockUser(UUID userId) {
		User user = mock(User.class);
		lenient().when(user.getId()).thenReturn(userId);
		lenient().when(user.getName()).thenReturn("테스트유저");
		lenient().when(user.getProfileImageUrl()).thenReturn(null);
		return user;
	}

	private Content mockContent(UUID contentId) {
		Content content = mock(Content.class);
		lenient().when(content.getId()).thenReturn(contentId);
		lenient().when(content.getType()).thenReturn(ContentType.movie);
		lenient().when(content.getTitle()).thenReturn("테스트콘텐츠");
		lenient().when(content.getDescription()).thenReturn("설명");
		lenient().when(content.getThumbnailUrl()).thenReturn(null);
		lenient().when(content.getAverageRating()).thenReturn(BigDecimal.ZERO);
		lenient().when(content.getReviewCount()).thenReturn(0L);
		return content;
	}

	private WatchingSessionPageRequest pageRequest(int limit, String cursor, UUID idAfter) {
		return new WatchingSessionPageRequest(
			null, cursor, idAfter, limit, SortDirection.ASCENDING, WatchingSessionSortBy.createdAt
		);
	}
}
