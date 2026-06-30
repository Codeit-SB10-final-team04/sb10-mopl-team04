package com.team04.mopl.playlist.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import com.team04.mopl.playlist.entity.Playlist;
import com.team04.mopl.playlist.entity.PlaylistSubscription;
import com.team04.mopl.playlist.exception.PlaylistException;
import com.team04.mopl.playlist.repository.PlaylistRepository;
import com.team04.mopl.playlist.repository.PlaylistSubscriptionRepository;
import com.team04.mopl.user.entity.User;
import com.team04.mopl.user.exception.UserException;
import com.team04.mopl.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class PlaylistSubscriptionServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private PlaylistRepository playlistRepository;

	@Mock
	private PlaylistSubscriptionRepository playlistSubscriptionRepository;

	@InjectMocks
	private PlaylistSubscriptionService playlistSubscriptionService;

	@Test
	@DisplayName("플레이리스트 구독에 성공하면 플레이리스트 구독 관계를 저장한다.")
	void subscribePlaylist_saveSubscription_whenValidRequest() {
		// given
		UUID playlistId = UUID.randomUUID();
		UUID currentUserId = UUID.randomUUID();
		UUID ownerId = UUID.randomUUID();

		User currentUser = createUser(currentUserId);
		User owner = createUser(ownerId);
		Playlist playlist = createPlaylist(owner, playlistId);

		when(userRepository.findByIdAndLockedFalse(currentUserId))
			.thenReturn(Optional.of(currentUser));
		when(playlistRepository.findByIdWithOwnerAndDeletedAtIsNull(playlistId))
			.thenReturn(Optional.of(playlist));
		when(playlistSubscriptionRepository.existsByPlaylistIdAndSubscriberId(playlistId, currentUserId))
			.thenReturn(Boolean.FALSE);

		// when
		playlistSubscriptionService.subscribePlaylist(playlistId, currentUserId);

		// then
		verify(userRepository).findByIdAndLockedFalse(currentUserId);
		verify(playlistRepository).findByIdWithOwnerAndDeletedAtIsNull(playlistId);
		verify(playlistSubscriptionRepository).existsByPlaylistIdAndSubscriberId(playlistId, currentUserId);

		ArgumentCaptor<PlaylistSubscription> playlistSubscriptionCaptor =
			ArgumentCaptor.forClass(PlaylistSubscription.class);
		verify(playlistSubscriptionRepository).saveAndFlush(playlistSubscriptionCaptor.capture());
	}

	@Test
	@DisplayName("존재하지 않은 사용자로 플레이리스트를 구독하면 예외가 발생한다.")
	void subscribePlaylist_throwException_whenUserNotFound() {
		// given
		UUID playlistId = UUID.randomUUID();
		UUID currentUserId = UUID.randomUUID();

		when(userRepository.findByIdAndLockedFalse(currentUserId))
			.thenReturn(Optional.empty());

		// when
		assertThrows(UserException.class,
			() -> playlistSubscriptionService.subscribePlaylist(playlistId, currentUserId));

		// then
		verify(userRepository).findByIdAndLockedFalse(currentUserId);
		verify(playlistRepository, never()).findByIdWithOwnerAndDeletedAtIsNull(any(UUID.class));
		verify(playlistSubscriptionRepository, never()).existsByPlaylistIdAndSubscriberId(any(UUID.class),
			any(UUID.class));
		verify(playlistSubscriptionRepository, never()).saveAndFlush(any(PlaylistSubscription.class));
	}

	@Test
	@DisplayName("존재하지 않은 플레이리스트로 플레이리스트를 구독하면 예외가 발생한다.")
	void subscribePlaylist_throwException_whenPlaylistNotFound() {
		// given
		UUID playlistId = UUID.randomUUID();
		UUID currentUserId = UUID.randomUUID();

		User currentUser = createUser(currentUserId);

		when(userRepository.findByIdAndLockedFalse(currentUserId))
			.thenReturn(Optional.of(currentUser));
		when(playlistRepository.findByIdWithOwnerAndDeletedAtIsNull(playlistId))
			.thenReturn(Optional.empty());

		// when
		assertThrows(PlaylistException.class,
			() -> playlistSubscriptionService.subscribePlaylist(playlistId, currentUserId));

		// then
		verify(userRepository).findByIdAndLockedFalse(currentUserId);
		verify(playlistRepository).findByIdWithOwnerAndDeletedAtIsNull(playlistId);
		verify(playlistSubscriptionRepository, never()).existsByPlaylistIdAndSubscriberId(any(UUID.class),
			any(UUID.class));
		verify(playlistSubscriptionRepository, never()).saveAndFlush(any(PlaylistSubscription.class));
	}

	@Test
	@DisplayName("구독 중복 요청 시 예외가 발생한다.")
	void subscribePlaylist_throwException_whenDuplicateSubscribe() {
		// given
		UUID playlistId = UUID.randomUUID();
		UUID currentUserId = UUID.randomUUID();
		UUID ownerId = UUID.randomUUID();

		User currentUser = createUser(currentUserId);
		User owner = createUser(ownerId);
		Playlist playlist = createPlaylist(owner, playlistId);

		when(userRepository.findByIdAndLockedFalse(currentUserId))
			.thenReturn(Optional.of(currentUser));
		when(playlistRepository.findByIdWithOwnerAndDeletedAtIsNull(playlistId))
			.thenReturn(Optional.of(playlist));
		when(playlistSubscriptionRepository.existsByPlaylistIdAndSubscriberId(playlistId, currentUserId))
			.thenReturn(Boolean.TRUE);

		// when
		assertThrows(PlaylistException.class,
			() -> playlistSubscriptionService.subscribePlaylist(playlistId, currentUserId));

		// then
		verify(userRepository).findByIdAndLockedFalse(currentUserId);
		verify(playlistRepository).findByIdWithOwnerAndDeletedAtIsNull(playlistId);
		verify(playlistSubscriptionRepository).existsByPlaylistIdAndSubscriberId(playlistId, currentUserId);
		verify(playlistSubscriptionRepository, never()).saveAndFlush(any(PlaylistSubscription.class));
	}

	@Test
	@DisplayName("구독 중복 요청 시 예외가 발생한다.")
	void subscribePlaylist_throwException_whenDuplicateAndConflictSubscribe() {
		// given
		UUID playlistId = UUID.randomUUID();
		UUID currentUserId = UUID.randomUUID();
		UUID ownerId = UUID.randomUUID();

		User currentUser = createUser(currentUserId);
		User owner = createUser(ownerId);
		Playlist playlist = createPlaylist(owner, playlistId);

		when(userRepository.findByIdAndLockedFalse(currentUserId))
			.thenReturn(Optional.of(currentUser));
		when(playlistRepository.findByIdWithOwnerAndDeletedAtIsNull(playlistId))
			.thenReturn(Optional.of(playlist));
		when(playlistSubscriptionRepository.existsByPlaylistIdAndSubscriberId(playlistId, currentUserId))
			.thenReturn(Boolean.FALSE);
		when(playlistSubscriptionRepository.saveAndFlush(any(PlaylistSubscription.class)))
			.thenThrow(new DataIntegrityViolationException("duplicate! conflict!"));

		// when
		assertThrows(PlaylistException.class,
			() -> playlistSubscriptionService.subscribePlaylist(playlistId, currentUserId));

		// then
		verify(userRepository).findByIdAndLockedFalse(currentUserId);
		verify(playlistRepository).findByIdWithOwnerAndDeletedAtIsNull(playlistId);
		verify(playlistSubscriptionRepository).existsByPlaylistIdAndSubscriberId(playlistId, currentUserId);
		verify(playlistSubscriptionRepository).saveAndFlush(any(PlaylistSubscription.class));
	}

	@Test
	@DisplayName("플레이리스트 구독 취소에 성공하면 플레이리스트 구독 관계를 삭제한다.")
	void unSubscribePlaylist_deleteSubscription_whenValidRequest() {
		// given
		UUID playlistId = UUID.randomUUID();
		UUID currentUserId = UUID.randomUUID();
		UUID ownerId = UUID.randomUUID();

		User currentUser = createUser(currentUserId);
		User owner = createUser(ownerId);
		Playlist playlist = createPlaylist(owner, playlistId);
		PlaylistSubscription playlistSubscription = createPlaylistSubscription(currentUser, playlist);

		when(playlistRepository.findByIdWithOwnerAndDeletedAtIsNull(playlistId))
			.thenReturn(Optional.of(playlist));
		when(playlistSubscriptionRepository.findByPlaylistIdAndSubscriberId(playlistId, currentUserId))
			.thenReturn(Optional.of(playlistSubscription));

		// when
		playlistSubscriptionService.unSubscribePlaylist(playlistId, currentUserId);

		// then
		verify(playlistRepository).findByIdWithOwnerAndDeletedAtIsNull(playlistId);
		verify(playlistSubscriptionRepository).findByPlaylistIdAndSubscriberId(playlistId, currentUserId);
		verify(playlistSubscriptionRepository).delete(playlistSubscription);
	}

	@Test
	@DisplayName("존재하지 않은 플레이리스트로 플레이리스트 구독 취소를 하면 예외가 발생한다.")
	void unSubscribePlaylist_throwException_whenPlaylistNotFound() {
		// given
		UUID playlistId = UUID.randomUUID();
		UUID currentUserId = UUID.randomUUID();

		when(playlistRepository.findByIdWithOwnerAndDeletedAtIsNull(playlistId))
			.thenReturn(Optional.empty());

		// when
		assertThrows(PlaylistException.class,
			() -> playlistSubscriptionService.unSubscribePlaylist(playlistId, currentUserId));

		// then
		verify(playlistRepository).findByIdWithOwnerAndDeletedAtIsNull(playlistId);
		verify(playlistSubscriptionRepository, never()).findByPlaylistIdAndSubscriberId(any(UUID.class),
			any(UUID.class));
		verify(playlistSubscriptionRepository, never()).delete(any(PlaylistSubscription.class));
	}

	@Test
	@DisplayName("존재하지 않은 플레이리스트 구독 관계로 플레이리스트 구독 취소를 하면 예외가 발생한다.")
	void unSubscribePlaylist_throwException_whenPlaylistSubscriptionNotFound() {
		// given
		UUID playlistId = UUID.randomUUID();
		UUID currentUserId = UUID.randomUUID();
		UUID ownerId = UUID.randomUUID();

		User owner = createUser(ownerId);
		Playlist playlist = createPlaylist(owner, playlistId);

		when(playlistRepository.findByIdWithOwnerAndDeletedAtIsNull(playlistId))
			.thenReturn(Optional.of(playlist));
		when(playlistSubscriptionRepository.findByPlaylistIdAndSubscriberId(playlistId, currentUserId))
			.thenReturn(Optional.empty());

		// when
		assertThrows(PlaylistException.class,
			() -> playlistSubscriptionService.unSubscribePlaylist(playlistId, currentUserId));

		// then
		verify(playlistRepository).findByIdWithOwnerAndDeletedAtIsNull(playlistId);
		verify(playlistSubscriptionRepository).findByPlaylistIdAndSubscriberId(playlistId, currentUserId);
		verify(playlistSubscriptionRepository, never()).delete(any(PlaylistSubscription.class));
	}

	private User createUser(UUID userId) {
		User user = User.builder()
			.name("테스트 사용자")
			.email("test@gmail.com")
			.profileImageUrl("https://example.com")
			.build();
		ReflectionTestUtils.setField(user, "id", userId);
		return user;
	}

	private Playlist createPlaylist(User owner, UUID playlistId) {
		Playlist playlist = Playlist.builder()
			.owner(owner)
			.title("테스트 제목")
			.description("테스트 설명")
			.build();
		ReflectionTestUtils.setField(playlist, "id", playlistId);
		return playlist;
	}

	private PlaylistSubscription createPlaylistSubscription(User subscriber, Playlist playlist) {
		PlaylistSubscription playlistSubscription = PlaylistSubscription.builder()
			.subscriber(subscriber)
			.playlist(playlist)
			.build();
		ReflectionTestUtils.setField(playlistSubscription, "id", UUID.randomUUID());
		return playlistSubscription;
	}
}