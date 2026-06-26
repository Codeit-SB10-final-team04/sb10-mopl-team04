package com.team04.mopl.playlist.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.team04.mopl.content.entity.Content;
import com.team04.mopl.content.entity.ContentType;
import com.team04.mopl.content.repository.ContentTagRepository;
import com.team04.mopl.playlist.dto.request.PlaylistCreateRequest;
import com.team04.mopl.playlist.dto.request.PlaylistUpdateRequest;
import com.team04.mopl.playlist.dto.response.PlaylistContentSummary;
import com.team04.mopl.playlist.dto.response.PlaylistDto;
import com.team04.mopl.playlist.dto.response.PlaylistUserSummary;
import com.team04.mopl.playlist.dto.row.PlaylistContentRow;
import com.team04.mopl.playlist.dto.row.PlaylistSubscriberCountRow;
import com.team04.mopl.playlist.entity.Playlist;
import com.team04.mopl.playlist.exception.PlaylistErrorCode;
import com.team04.mopl.playlist.exception.PlaylistException;
import com.team04.mopl.playlist.mapper.PlaylistMapper;
import com.team04.mopl.playlist.repository.PlaylistContentRepository;
import com.team04.mopl.playlist.repository.PlaylistRepository;
import com.team04.mopl.playlist.repository.PlaylistSubscriptionRepository;
import com.team04.mopl.user.entity.User;
import com.team04.mopl.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class PlaylistServiceTest {

	@Mock
	private UserRepository userRepository;

	@Mock
	private ContentTagRepository contentTagRepository;

	@Mock
	private PlaylistRepository playlistRepository;

	@Mock
	private PlaylistSubscriptionRepository playlistSubscriptionRepository;

	@Mock
	private PlaylistContentRepository playlistContentRepository;

	@Mock
	private PlaylistMapper playlistMapper;

	@InjectMocks
	private PlaylistService playlistService;

	@Test
	@DisplayName("플레이리스트 생성에 성공하면 생성된 플레이리스트 DTO를 반환한다.")
	void createPlaylist_returnPlaylistDto_whenValidRequest() {
		// given
		UUID currentUserId = UUID.randomUUID();
		UUID playlistId = UUID.randomUUID();
		Instant updatedAt = Instant.parse("2026-06-24T01:00:00Z");

		PlaylistCreateRequest request = new PlaylistCreateRequest("테스트 제목", "테스트 설명");
		User owner = createUser(currentUserId);

		// TODO: UserSummary 구현 후 변경
		// UserSummary ownerSummary = new UserSummary(owner.getId(), owner.getName(), owner.getProfileImageUrl());
		PlaylistUserSummary ownerSummary = new PlaylistUserSummary(owner.getId(), owner.getName(),
			owner.getProfileImageUrl());

		PlaylistDto mapperResult = new PlaylistDto(
			playlistId,
			ownerSummary,
			request.title(),
			request.description(),
			updatedAt,
			0L,
			false,
			List.of()
		);

		when(userRepository.findById(currentUserId))
			.thenReturn(Optional.of(owner));
		when(playlistMapper.toDto(
				any(Playlist.class),
				// TODO: UserSummary 구현 후 변경
				// any(UserSummary.class),
				any(PlaylistUserSummary.class),
				anyLong(),
				anyBoolean(),
				anyList()
			)
		).thenReturn(mapperResult);

		// when
		PlaylistDto result = playlistService.createPlaylist(request, currentUserId);

		// then
		assertEquals(mapperResult, result);

		ArgumentCaptor<Playlist> playlistCaptor = ArgumentCaptor.forClass(Playlist.class);
		ArgumentCaptor<Long> subscriberCountCaptor = ArgumentCaptor.forClass(Long.class);
		ArgumentCaptor<Boolean> subscribedByMeCaptor = ArgumentCaptor.forClass(Boolean.class);
		ArgumentCaptor<List<PlaylistContentSummary>> contentCaptor = ArgumentCaptor.forClass(List.class);

		verify(userRepository).findById(currentUserId);
		verify(playlistRepository).save(playlistCaptor.capture());

		verify(playlistMapper).toDto(
			playlistCaptor.capture(),
			// TODO: UserSummary 구현 후 변경
			// any(UserSummary.class),
			any(PlaylistUserSummary.class),
			subscriberCountCaptor.capture(),
			subscribedByMeCaptor.capture(),
			contentCaptor.capture()
		);

		assertEquals(0L, subscriberCountCaptor.getValue());
		assertEquals(false, subscribedByMeCaptor.getValue());
		assertEquals(0, contentCaptor.getValue().size());
	}

	@Test
	@DisplayName("존재하지 않는 사용자 id로 플레이리스트 생성 시 예외가 발생한다.")
	void createPlaylist_throwException_whenUserNotFound() {
		// given
		UUID currentUserId = UUID.randomUUID();
		PlaylistCreateRequest request = new PlaylistCreateRequest("테스트 제목", "테스트 설명");

		// TODO: USER_NOT_FOUND 같은 사용자 커스텀 예외 추가 시 `IllegalArgumentException.class` 수정
		when(userRepository.findById(currentUserId))
			.thenReturn(Optional.empty());

		// when, then
		// TODO: USER_NOT_FOUND 같은 사용자 커스텀 예외 추가 시 `IllegalArgumentException.class` 수정
		assertThrows(IllegalArgumentException.class,
			() -> playlistService.createPlaylist(request, currentUserId));

		verify(userRepository).findById(currentUserId);
		verify(playlistRepository, never()).save(any(Playlist.class));
		verify(playlistMapper, never()).toDto(
			any(Playlist.class),
			// TODO: UserSummary 구현 후 변경
			// any(UserSummary.class),
			any(PlaylistUserSummary.class),
			anyLong(),
			anyBoolean(),
			anyList()
		);
	}

	@Test
	@DisplayName("플레이리스트 단건 조회에 성공하면 플레이리스트 DTO를 반환된다.")
	void findPlaylist_returnPlaylistDto_whenValidRequest() {
		// given
		UUID currentUserId = UUID.randomUUID();
		UUID ownerId = UUID.randomUUID();
		UUID playlistId = UUID.randomUUID();
		UUID contentId = UUID.randomUUID();
		Instant updatedAt = Instant.parse("2026-06-24T01:00:00Z");

		User currentUser = createUser(currentUserId);
		User owner = createUser(ownerId);
		Playlist playlist = createPlaylist(owner, playlistId);
		Content content = createContent(contentId);

		// TODO: UserSummary 구현 후 변경
		// UserSummary ownerSummary = new UserSummary(owner.getId(), owner.getName(), owner.getProfileImageUrl());
		PlaylistUserSummary ownerSummary = new PlaylistUserSummary(owner.getId(), owner.getName(),
			owner.getProfileImageUrl());

		// TODO: ContentSummary 구현 후 변경
		PlaylistContentSummary contentSummary = new PlaylistContentSummary(
			content.getId(),
			content.getType(),
			content.getTitle(),
			content.getDescription(),
			content.getThumbnailUrl(),
			List.of("액션", "드라마"),
			BigDecimal.valueOf(5L),
			3L
		);

		PlaylistDto mapperResult = new PlaylistDto(
			playlistId,
			ownerSummary,
			playlist.getTitle(),
			playlist.getDescription(),
			updatedAt,
			5L,
			true,
			List.of(contentSummary)
		);

		when(userRepository.findById(currentUserId))
			.thenReturn(Optional.of(currentUser));
		when(playlistRepository.findByIdWithOwnerAndDeletedAtIsNull(playlistId))
			.thenReturn(Optional.of(playlist));
		when(playlistSubscriptionRepository.countAllSubscribersByPlaylistIds(List.of(playlistId)))
			.thenReturn(List.of(new PlaylistSubscriberCountRow(playlistId, 5L)));
		when(playlistSubscriptionRepository.findSubscribedPlaylistIds(List.of(playlistId), currentUserId))
			.thenReturn(Set.of(playlistId));
		when(playlistContentRepository.findAllContentsByPlaylistIdsWithDeletedAtNull(List.of(playlistId)))
			.thenReturn(List.of(new PlaylistContentRow(playlistId, content)));
		when(playlistMapper.toDto(
				any(Playlist.class),
				// TODO: UserSummary 구현 후 변경
				// any(UserSummary.class),
				any(PlaylistUserSummary.class),
				anyLong(),
				anyBoolean(),
				anyList()
			)
		).thenReturn(mapperResult);

		// when
		PlaylistDto result = playlistService.findPlaylist(playlistId, currentUserId);

		// then
		assertEquals(mapperResult, result);

		verify(userRepository).findById(currentUserId);
		verify(playlistRepository).findByIdWithOwnerAndDeletedAtIsNull(playlistId);
		verify(playlistSubscriptionRepository).countAllSubscribersByPlaylistIds(List.of(playlistId));
		verify(playlistSubscriptionRepository).findSubscribedPlaylistIds(List.of(playlistId), currentUserId);
		verify(playlistContentRepository).findAllContentsByPlaylistIdsWithDeletedAtNull(List.of(playlistId));

		ArgumentCaptor<Long> subscriberCountCaptor = ArgumentCaptor.forClass(Long.class);
		ArgumentCaptor<Boolean> subscribedByMeCaptor = ArgumentCaptor.forClass(Boolean.class);
		ArgumentCaptor<List<PlaylistContentSummary>> contentCaptor = ArgumentCaptor.forClass(List.class);

		verify(playlistMapper).toDto(
			eq(playlist),
			// TODO: UserSummary 구현 후 변경
			// any(UserSummary.class),
			eq(ownerSummary),
			subscriberCountCaptor.capture(),
			subscribedByMeCaptor.capture(),
			contentCaptor.capture()
		);

		assertEquals(5L, subscriberCountCaptor.getValue());
		assertEquals(true, subscribedByMeCaptor.getValue());
		assertEquals(1, contentCaptor.getValue().size());
		assertEquals(contentId, contentCaptor.getValue().get(0).id());
	}

	@Test
	@DisplayName("플레이리스트 단건 조회 시 현재 사용자가 존재하지 않으면 예외가 발생한다.")
	void findPlaylist_throwException_whenUserNotFound() {
		// given
		UUID currentUserId = UUID.randomUUID();
		UUID playlistId = UUID.randomUUID();

		// TODO: USER_NOT_FOUND 같은 사용자 커스텀 예외 추가 시 `IllegalArgumentException.class` 수정
		when(userRepository.findById(currentUserId))
			.thenReturn(Optional.empty());

		// when, then
		// TODO: USER_NOT_FOUND 같은 사용자 커스텀 예외 추가 시 `IllegalArgumentException.class` 수정
		assertThrows(IllegalArgumentException.class,
			() -> playlistService.findPlaylist(playlistId, currentUserId));

		verify(userRepository).findById(currentUserId);
		verify(playlistRepository, never()).findByIdWithOwnerAndDeletedAtIsNull(any(UUID.class));
		verify(playlistSubscriptionRepository, never()).countAllSubscribersByPlaylistIds(anyList());
		verify(playlistSubscriptionRepository, never()).findSubscribedPlaylistIds(anyList(), any(UUID.class));
		verify(playlistContentRepository, never()).findAllContentsByPlaylistIdsWithDeletedAtNull(anyList());
		verify(playlistMapper, never()).toDto(
			any(Playlist.class),
			// TODO: UserSummary 구현 후 변경
			// any(UserSummary.class),
			any(PlaylistUserSummary.class),
			anyLong(),
			anyBoolean(),
			anyList()
		);
	}

	@Test
	@DisplayName("플레이리스트 단건 조회 시 플레이리스트가 존재하지 않으면 예외가 발생한다.")
	void findPlaylist_throwException_whenPlaylistNotFound() {
		// given
		UUID currentUserId = UUID.randomUUID();
		UUID playlistId = UUID.randomUUID();

		User currentUser = createUser(currentUserId);

		// TODO: USER_NOT_FOUND 같은 사용자 커스텀 예외 추가 시 `IllegalArgumentException.class` 수정
		when(userRepository.findById(currentUserId))
			.thenReturn(Optional.of(currentUser));
		when(playlistRepository.findByIdWithOwnerAndDeletedAtIsNull(playlistId))
			.thenReturn(Optional.empty());

		// when, then
		PlaylistException exception = assertThrows(PlaylistException.class,
			() -> playlistService.findPlaylist(playlistId, currentUserId));
		assertEquals(PlaylistErrorCode.PLAYLIST_NOT_FOUND, exception.getErrorCode());

		verify(userRepository).findById(currentUserId);
		verify(playlistRepository).findByIdWithOwnerAndDeletedAtIsNull(playlistId);
		verify(playlistSubscriptionRepository, never()).countAllSubscribersByPlaylistIds(anyList());
		verify(playlistSubscriptionRepository, never()).findSubscribedPlaylistIds(anyList(), any(UUID.class));
		verify(playlistContentRepository, never()).findAllContentsByPlaylistIdsWithDeletedAtNull(anyList());
		verify(playlistMapper, never()).toDto(
			any(Playlist.class),
			// TODO: UserSummary 구현 후 변경
			// any(UserSummary.class),
			any(PlaylistUserSummary.class),
			anyLong(),
			anyBoolean(),
			anyList()
		);
	}

	@Test
	@DisplayName("플레이리스트 수정에 성공하면 수정된 플레이리스트 DTO를 반환한다.")
	void updatePlaylist_returnPlaylistDto_whenValidRequest() {
		// given
		UUID currentUserId = UUID.randomUUID();
		UUID playlistId = UUID.randomUUID();
		Instant updatedAt = Instant.parse("2026-06-24T01:00:00Z");

		PlaylistUpdateRequest request = new PlaylistUpdateRequest("수정 title", "수정 description");
		User owner = createUser(currentUserId);
		Playlist playlist = createPlaylist(owner, playlistId);

		// TODO: UserSummary 구현 후 변경
		// UserSummary ownerSummary = new UserSummary(owner.getId(), owner.getName(), owner.getProfileImageUrl());
		PlaylistUserSummary ownerSummary = new PlaylistUserSummary(owner.getId(), owner.getName(),
			owner.getProfileImageUrl());

		PlaylistDto mapperResult = new PlaylistDto(
			playlistId,
			ownerSummary,
			request.title(),
			request.description(),
			updatedAt,
			0L,
			false,
			List.of()
		);

		when(playlistRepository.findByIdWithOwnerAndDeletedAtIsNull(playlistId))
			.thenReturn(Optional.of(playlist));
		when(playlistRepository.findByIdWithOwnerAndDeletedAtIsNull(playlistId))
			.thenReturn(Optional.of(playlist));
		when(playlistSubscriptionRepository.countAllSubscribersByPlaylistIds(List.of(playlistId)))
			.thenReturn(List.of(new PlaylistSubscriberCountRow(playlistId, 0L)));
		when(playlistSubscriptionRepository.findSubscribedPlaylistIds(List.of(playlistId), currentUserId))
			.thenReturn(Set.of());
		when(playlistContentRepository.findAllContentsByPlaylistIdsWithDeletedAtNull(List.of(playlistId)))
			.thenReturn(List.of());
		when(playlistMapper.toDto(
				any(Playlist.class),
				// TODO: UserSummary 구현 후 변경
				// any(UserSummary.class),
				any(PlaylistUserSummary.class),
				anyLong(),
				anyBoolean(),
				anyList()
			)
		).thenReturn(mapperResult);

		// when
		PlaylistDto result = playlistService.updatePlaylist(playlistId, request, currentUserId);

		// then
		assertEquals(mapperResult, result);

		verify(playlistRepository).findByIdWithOwnerAndDeletedAtIsNull(playlistId);
		verify(playlistSubscriptionRepository).countAllSubscribersByPlaylistIds(List.of(playlistId));
		verify(playlistSubscriptionRepository).findSubscribedPlaylistIds(List.of(playlistId), currentUserId);
		verify(playlistContentRepository).findAllContentsByPlaylistIdsWithDeletedAtNull(List.of(playlistId));

		ArgumentCaptor<Long> subscriberCountCaptor = ArgumentCaptor.forClass(Long.class);
		ArgumentCaptor<Boolean> subscribedByMeCaptor = ArgumentCaptor.forClass(Boolean.class);
		ArgumentCaptor<List<PlaylistContentSummary>> contentCaptor = ArgumentCaptor.forClass(List.class);

		verify(playlistMapper).toDto(
			eq(playlist),
			// TODO: UserSummary 구현 후 변경
			// any(UserSummary.class),
			any(PlaylistUserSummary.class),
			subscriberCountCaptor.capture(),
			subscribedByMeCaptor.capture(),
			contentCaptor.capture()
		);

		assertEquals(0L, subscriberCountCaptor.getValue());
		assertEquals(false, subscribedByMeCaptor.getValue());
		assertEquals(0, contentCaptor.getValue().size());
	}

	@Test
	@DisplayName("플레이리스트 수정 시에 플레이리스트 제목이나 설명이 공백이면 예외가 발생한다.")
	void updatePlaylist_returnException_whenTitleOrDescriptionIsBlank() {
		// given
		UUID currentUserId = UUID.randomUUID();
		UUID playlistId = UUID.randomUUID();

		PlaylistUpdateRequest request = new PlaylistUpdateRequest(" ", "수정 description");

		// when, then
		PlaylistException exception = assertThrows(PlaylistException.class,
			() -> playlistService.updatePlaylist(playlistId, request, currentUserId));
		assertEquals(PlaylistErrorCode.INVALID_INPUT, exception.getErrorCode());

		verify(playlistRepository, never()).findByIdWithOwnerAndDeletedAtIsNull(any(UUID.class));
		verify(playlistSubscriptionRepository, never()).countAllSubscribersByPlaylistIds(anyList());
		verify(playlistSubscriptionRepository, never()).findSubscribedPlaylistIds(anyList(), any(UUID.class));
		verify(playlistContentRepository, never()).findAllContentsByPlaylistIdsWithDeletedAtNull(anyList());

		verify(playlistMapper, never()).toDto(
			any(Playlist.class),
			// TODO: UserSummary 구현 후 변경
			// any(UserSummary.class),
			any(PlaylistUserSummary.class),
			anyLong(),
			anyBoolean(),
			anyList()
		);
	}

	@Test
	@DisplayName("플레이리스트 수정 시에 플레이리스트 소유자가 아니면 예외가 발생한다.")
	void updatePlaylist_returnException_whenCurrentUserIsNotOwner() {
		// given
		UUID currentUserId = UUID.randomUUID();
		UUID ownerId = UUID.randomUUID();
		UUID playlistId = UUID.randomUUID();

		PlaylistUpdateRequest request = new PlaylistUpdateRequest("수정 title", "수정 description");
		User owner = createUser(ownerId);
		Playlist playlist = createPlaylist(owner, playlistId);

		when(playlistRepository.findByIdWithOwnerAndDeletedAtIsNull(playlistId))
			.thenReturn(Optional.of(playlist));

		// when, then
		PlaylistException exception = assertThrows(PlaylistException.class,
			() -> playlistService.updatePlaylist(playlistId, request, currentUserId));

		assertEquals(PlaylistErrorCode.PLAYLIST_FORBIDDEN, exception.getErrorCode());

		verify(playlistRepository).findByIdWithOwnerAndDeletedAtIsNull(playlistId);
		verify(playlistSubscriptionRepository, never()).countAllSubscribersByPlaylistIds(anyList());
		verify(playlistSubscriptionRepository, never()).findSubscribedPlaylistIds(anyList(), any(UUID.class));
		verify(playlistContentRepository, never()).findAllContentsByPlaylistIdsWithDeletedAtNull(anyList());

		verify(playlistMapper, never()).toDto(
			any(Playlist.class),
			// TODO: UserSummary 구현 후 변경
			// any(UserSummary.class),
			any(PlaylistUserSummary.class),
			anyLong(),
			anyBoolean(),
			anyList()
		);
	}

	@Test
	@DisplayName("플레이리스트 수정 시에 플레이리스트 제목과 설명에 변경사항이 없다면 예외가 발생한다.")
	void updatePlaylist_returnException_whenNotChange() {
		// given
		UUID currentUserId = UUID.randomUUID();
		UUID playlistId = UUID.randomUUID();

		PlaylistUpdateRequest request = new PlaylistUpdateRequest("테스트 제목", "테스트 설명");
		User owner = createUser(currentUserId);
		Playlist playlist = createPlaylist(owner, playlistId);

		when(playlistRepository.findByIdWithOwnerAndDeletedAtIsNull(playlistId))
			.thenReturn(Optional.of(playlist));

		// when, then
		PlaylistException exception = assertThrows(PlaylistException.class,
			() -> playlistService.updatePlaylist(playlistId, request, currentUserId));
		assertEquals(PlaylistErrorCode.NO_CHANGE_VALUE, exception.getErrorCode());

		verify(playlistRepository).findByIdWithOwnerAndDeletedAtIsNull(playlistId);
		verify(playlistSubscriptionRepository, never()).countAllSubscribersByPlaylistIds(anyList());
		verify(playlistSubscriptionRepository, never()).findSubscribedPlaylistIds(anyList(), any(UUID.class));
		verify(playlistContentRepository, never()).findAllContentsByPlaylistIdsWithDeletedAtNull(anyList());

		verify(playlistMapper, never()).toDto(
			any(Playlist.class),
			// TODO: UserSummary 구현 후 변경
			// any(UserSummary.class),
			any(PlaylistUserSummary.class),
			anyLong(),
			anyBoolean(),
			anyList()
		);
	}

	@Test
	@DisplayName("플레이리스트 논리 삭제에 성공하면 deletedAt이 기록된다.")
	void softDeletePlaylist_markDeletedAt_whenValidRequest() {
		// given
		UUID currentUserId = UUID.randomUUID();
		UUID playlistId = UUID.randomUUID();

		User owner = createUser(currentUserId);
		Playlist playlist = createPlaylist(owner, playlistId);

		when(playlistRepository.findByIdWithOwnerAndDeletedAtIsNull(playlistId))
			.thenReturn(Optional.of(playlist));

		// when
		playlistService.softDeletePlaylist(playlistId, currentUserId);

		// then
		assertNotNull(playlist.getDeletedAt());

		verify(playlistRepository).findByIdWithOwnerAndDeletedAtIsNull(playlistId);
	}

	@Test
	@DisplayName("플레이리스트 논리 삭제 시 플레이리스트가 존재하지 않으면 예외가 발생한다.")
	void softDeletePlaylist_throwException_whenPlaylistNotFound() {
		//given
		UUID playlistId = UUID.randomUUID();
		UUID currentUserId = UUID.randomUUID();

		when(playlistRepository.findByIdWithOwnerAndDeletedAtIsNull(playlistId))
			.thenReturn(Optional.empty());

		// when, then
		assertThrows(PlaylistException.class,
			() -> playlistService.softDeletePlaylist(playlistId, currentUserId));

		verify(playlistRepository).findByIdWithOwnerAndDeletedAtIsNull(playlistId);
	}

	@Test
	@DisplayName("플레이리스트 논리 삭제 시 플레이리스트 소유자가 아니면 예외가 발생한다.")
	void softDeletePlaylist_throwException_whenCurrentUserIsNotOwner() {
		// given
		UUID playlistId = UUID.randomUUID();
		UUID ownerId = UUID.randomUUID();
		UUID currentUserId = UUID.randomUUID();

		User owner = createUser(ownerId);
		Playlist playlist = createPlaylist(owner, playlistId);

		when(playlistRepository.findByIdWithOwnerAndDeletedAtIsNull(playlistId))
			.thenReturn(Optional.of(playlist));

		// when, then
		PlaylistException exception = assertThrows(PlaylistException.class,
			() -> playlistService.softDeletePlaylist(playlistId, currentUserId));

		assertEquals(PlaylistErrorCode.PLAYLIST_FORBIDDEN, exception.getErrorCode());
		assertNull(playlist.getDeletedAt());

		verify(playlistRepository).findByIdWithOwnerAndDeletedAtIsNull(playlistId);
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

	private Content createContent(UUID contentId) {
		Content content = Content.builder()
			.title("테스트 제목")
			.type(ContentType.movie)
			.description("콘텐츠 설명")
			.thumbnailUrl("https://thumbnail.url")
			.build();
		ReflectionTestUtils.setField(content, "id", contentId);
		return content;
	}
}