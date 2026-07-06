package com.team04.mopl.watching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.team04.mopl.content.entity.Content;
import com.team04.mopl.content.entity.ContentType;
import com.team04.mopl.content.repository.ContentRepository;
import com.team04.mopl.user.entity.User;
import com.team04.mopl.user.exception.UserException;
import com.team04.mopl.user.repository.UserRepository;
import com.team04.mopl.watching.dto.response.WatchingSessionChange;
import com.team04.mopl.watching.enums.ChangeType;
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

		when(watchingSessionStore.addWatcher(contentId, userId)).thenReturn(true);
		when(watchingSessionStore.getWatcherCount(contentId)).thenReturn(1L);

		User user = mock(User.class);
		when(user.getId()).thenReturn(userId);
		when(user.getName()).thenReturn("테스트유저");
		when(user.getProfileImageUrl()).thenReturn(null);
		when(userRepository.findByIdAndLockedFalse(userId)).thenReturn(Optional.of(user));

		Content content = mock(Content.class);
		when(content.getId()).thenReturn(contentId);
		when(content.getType()).thenReturn(ContentType.movie);
		when(content.getTitle()).thenReturn("테스트콘텐츠");
		when(content.getDescription()).thenReturn("설명");
		when(content.getThumbnailUrl()).thenReturn(null);
		when(content.getAverageRating()).thenReturn(BigDecimal.ZERO);
		when(contentRepository.findById(contentId)).thenReturn(Optional.of(content));

		// when
		Optional<WatchingSessionChange> result = watchingSessionService.join(contentId, userId);

		// then
		assertThat(result).isPresent();
		assertThat(result.get().type()).isEqualTo(ChangeType.JOIN);
		assertThat(result.get().watcherCount()).isEqualTo(1L);
		assertThat(result.get().watchingSession().watcher().userId()).isEqualTo(userId);
	}

	@Test
	@DisplayName("이미 시청 중인 유저가 입장하면 empty를 반환한다")
	void join_returnsEmpty_whenAlreadyWatching() {
		// given
		UUID contentId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();

		when(watchingSessionStore.addWatcher(contentId, userId)).thenReturn(false);

		// when
		Optional<WatchingSessionChange> result = watchingSessionService.join(contentId, userId);

		// then
		assertThat(result).isEmpty();
		verifyNoInteractions(userRepository, contentRepository);
	}

	@Test
	@DisplayName("시청 세션 퇴장 시 LEAVE 이벤트를 반환한다")
	void leave_returnsLeaveChange_whenWatching() {
		// given
		UUID contentId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();

		when(watchingSessionStore.removeWatcher(contentId, userId)).thenReturn(true);
		when(watchingSessionStore.getWatcherCount(contentId)).thenReturn(0L);

		User user = mock(User.class);
		when(user.getId()).thenReturn(userId);
		when(user.getName()).thenReturn("테스트유저");
		when(user.getProfileImageUrl()).thenReturn(null);
		when(userRepository.findByIdAndLockedFalse(userId)).thenReturn(Optional.of(user));

		Content content = mock(Content.class);
		when(content.getId()).thenReturn(contentId);
		when(content.getType()).thenReturn(ContentType.movie);
		when(content.getTitle()).thenReturn("테스트콘텐츠");
		when(content.getDescription()).thenReturn("설명");
		when(content.getThumbnailUrl()).thenReturn(null);
		when(content.getAverageRating()).thenReturn(BigDecimal.ZERO);
		when(contentRepository.findById(contentId)).thenReturn(Optional.of(content));

		// when
		Optional<WatchingSessionChange> result = watchingSessionService.leave(contentId, userId);

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

		when(watchingSessionStore.removeWatcher(contentId, userId)).thenReturn(false);

		// when
		Optional<WatchingSessionChange> result = watchingSessionService.leave(contentId, userId);

		// then
		assertThat(result).isEmpty();
		verifyNoInteractions(userRepository, contentRepository);
	}

	@Test
	@DisplayName("존재하지 않는 유저로 입장하면 UserException을 던진다")
	void join_throwsException_whenUserNotFound() {
		// given
		UUID contentId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();

		when(watchingSessionStore.addWatcher(contentId, userId)).thenReturn(true);
		when(userRepository.findByIdAndLockedFalse(userId)).thenReturn(Optional.empty());

		// when & then
		assertThatThrownBy(() -> watchingSessionService.join(contentId, userId))
			.isInstanceOf(UserException.class);
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
}
