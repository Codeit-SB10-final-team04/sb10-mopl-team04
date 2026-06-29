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

import com.team04.mopl.common.dto.ContentSummary;
import com.team04.mopl.common.dto.UserSummary;
import com.team04.mopl.common.enums.SortDirection;
import com.team04.mopl.content.dto.row.TagRow;
import com.team04.mopl.content.entity.Content;
import com.team04.mopl.content.entity.ContentType;
import com.team04.mopl.content.repository.ContentTagRepository;
import com.team04.mopl.playlist.dto.request.PlaylistCreateRequest;
import com.team04.mopl.playlist.dto.request.PlaylistSearchRequest;
import com.team04.mopl.playlist.dto.request.PlaylistUpdateRequest;
import com.team04.mopl.playlist.dto.response.CursorResponsePlaylistDto;
import com.team04.mopl.playlist.dto.response.PlaylistCursorPage;
import com.team04.mopl.playlist.dto.response.PlaylistDto;
import com.team04.mopl.playlist.dto.row.PlaylistContentRow;
import com.team04.mopl.playlist.dto.row.PlaylistRow;
import com.team04.mopl.playlist.dto.row.PlaylistSubscriberCountRow;
import com.team04.mopl.playlist.entity.Playlist;
import com.team04.mopl.playlist.enums.PlaylistSortBy;
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
	private PlaylistRepository playlistRepository;

	@Mock
	private ContentTagRepository contentTagRepository;

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

		UserSummary ownerSummary = new UserSummary(owner.getId(), owner.getName(),
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
				any(UserSummary.class),
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
		ArgumentCaptor<List<ContentSummary>> contentCaptor = ArgumentCaptor.forClass(List.class);

		verify(userRepository).findById(currentUserId);
		verify(playlistRepository).save(playlistCaptor.capture());

		verify(playlistMapper).toDto(
			playlistCaptor.capture(),
			any(UserSummary.class),
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
			any(UserSummary.class),
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

		UserSummary ownerSummary = new UserSummary(owner.getId(), owner.getName(),
			owner.getProfileImageUrl());

		ContentSummary contentSummary = new ContentSummary(
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
		when(contentTagRepository.findTagNamesByContentIds(List.of(contentId)))
			.thenReturn(List.of(
				new TagRow(contentId, "액션"),
				new TagRow(contentId, "드라마")
			));
		when(playlistMapper.toDto(
				any(Playlist.class),
				any(UserSummary.class),
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
		ArgumentCaptor<List<ContentSummary>> contentCaptor = ArgumentCaptor.forClass(List.class);

		verify(playlistMapper).toDto(
			eq(playlist),
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
			any(UserSummary.class),
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
			any(UserSummary.class),
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

		UserSummary ownerSummary = new UserSummary(owner.getId(), owner.getName(),
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
				any(UserSummary.class),
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
		ArgumentCaptor<List<ContentSummary>> contentCaptor = ArgumentCaptor.forClass(List.class);

		verify(playlistMapper).toDto(
			eq(playlist),
			any(UserSummary.class),
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
			any(UserSummary.class),
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
			any(UserSummary.class),
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
			any(UserSummary.class),
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

	@Test
	@DisplayName("정렬 조건이 updatedAt인 플레이리스트 목록 조회에 성공하면 플레이리스트 커서 페이지네이션 응답 DTO를 반환한다.")
	void findAllPlaylists_returnCursorResponse_whenSortByUpdatedAt() {
		// given
		UUID currentUserId = UUID.randomUUID();
		UUID playlistId1 = UUID.randomUUID();
		UUID playlistId2 = UUID.randomUUID();
		UUID contentId = UUID.randomUUID();
		Instant updatedAt1 = Instant.parse("2026-06-24T01:00:00Z");
		Instant updatedAt2 = Instant.parse("2026-06-24T00:00:00Z");

		User currentUser = createUser(currentUserId);
		Playlist playlist1 = createPlaylist(currentUser, playlistId1);
		Playlist playlist2 = createPlaylist(currentUser, playlistId2);
		ReflectionTestUtils.setField(playlist1, "updatedAt", updatedAt1);
		ReflectionTestUtils.setField(playlist2, "updatedAt", updatedAt2);

		Content content = createContent(contentId);

		PlaylistSearchRequest request = new PlaylistSearchRequest(
			"제목",
			null, null, null, null,
			2,
			SortDirection.DESCENDING,
			PlaylistSortBy.updatedAt
		);

		PlaylistRow playlistRow1 = new PlaylistRow(
			playlist1,
			2L,
			currentUserId,
			currentUser.getName(),
			currentUser.getProfileImageUrl()
		);
		PlaylistRow playlistRow2 = new PlaylistRow(
			playlist2,
			3L,
			currentUserId,
			currentUser.getName(),
			currentUser.getProfileImageUrl()
		);

		PlaylistCursorPage playlistCursorPage = new PlaylistCursorPage(
			List.of(playlistRow1, playlistRow2),
			true,
			3L
		);

		PlaylistContentRow playlistContentRow = new PlaylistContentRow(playlistId2, content);

		UserSummary userSummary = new UserSummary(
			currentUserId,
			currentUser.getName(),
			currentUser.getProfileImageUrl()
		);

		ContentSummary contentSummary = new ContentSummary(
			contentId,
			content.getType(),
			content.getTitle(),
			content.getDescription(),
			content.getThumbnailUrl(),
			List.of(),
			content.getAverageRating(),
			content.getReviewCount()
		);

		PlaylistDto playlistDto1 = new PlaylistDto(
			playlistId1,
			userSummary,
			playlist1.getTitle(),
			playlist1.getDescription(),
			updatedAt1,
			2L,
			false,
			List.of()
		);

		PlaylistDto playlistDto2 = new PlaylistDto(
			playlistId2,
			userSummary,
			playlist2.getTitle(),
			playlist2.getDescription(),
			updatedAt2,
			3L,
			false,
			List.of(contentSummary)
		);

		CursorResponsePlaylistDto cursorResponsePlaylistDtoResult = new CursorResponsePlaylistDto(
			List.of(playlistDto1, playlistDto2),
			playlist2.getUpdatedAt().toString(),
			playlist2.getId(),
			true,
			3L,
			PlaylistSortBy.updatedAt.toString(),
			SortDirection.DESCENDING
		);

		when(userRepository.findById(currentUserId))
			.thenReturn(Optional.of(currentUser));
		when(playlistRepository.findAllPlaylists(request))
			.thenReturn(playlistCursorPage);
		when(playlistSubscriptionRepository.findSubscribedPlaylistIds(
			List.of(playlistId1, playlistId2),
			currentUserId)
		).thenReturn(Set.of());
		when(playlistContentRepository.findAllContentsByPlaylistIdsWithDeletedAtNull(
			List.of(playlistId1, playlistId2))
		).thenReturn(List.of(playlistContentRow));
		when(contentTagRepository.findTagNamesByContentIds(List.of(contentId)))
			.thenReturn(List.of());
		when(playlistMapper.toDto(
				any(Playlist.class),
				any(UserSummary.class),
				anyLong(),
				anyBoolean(),
				anyList()
			)
		).thenReturn(playlistDto1, playlistDto2);

		// when
		CursorResponsePlaylistDto result = playlistService.findAllPlaylists(request, currentUserId);

		// then
		assertEquals(cursorResponsePlaylistDtoResult, result);
		assertEquals(List.of(playlistDto1, playlistDto2), result.data());
		assertEquals(updatedAt2.toString(), result.nextCursor());
		assertEquals(playlist2.getId(), result.nextIdAfter());
		assertEquals(true, result.hasNext());
		assertEquals(3L, result.totalCount());
		assertEquals(PlaylistSortBy.updatedAt.toString(), result.sortBy());
		assertEquals(SortDirection.DESCENDING, result.sortDirection());

		verify(userRepository).findById(currentUserId);
		verify(playlistRepository).findAllPlaylists(request);
		verify(playlistSubscriptionRepository).findSubscribedPlaylistIds(
			List.of(playlistId1, playlistId2),
			currentUserId
		);
		verify(playlistContentRepository).findAllContentsByPlaylistIdsWithDeletedAtNull(
			List.of(playlistId1, playlistId2)
		);
		verify(contentTagRepository).findTagNamesByContentIds(List.of(contentId));

		ArgumentCaptor<Playlist> playlistCaptor = ArgumentCaptor.forClass(Playlist.class);
		ArgumentCaptor<UserSummary> ownerUserSummary = ArgumentCaptor.forClass(UserSummary.class);
		ArgumentCaptor<Long> subscriberCountCaptor = ArgumentCaptor.forClass(Long.class);
		ArgumentCaptor<Boolean> subscribedByMeCaptor = ArgumentCaptor.forClass(Boolean.class);
		ArgumentCaptor<List<ContentSummary>> contentCaptor = ArgumentCaptor.forClass(List.class);

		verify(playlistMapper, times(2)).toDto(
			playlistCaptor.capture(),
			ownerUserSummary.capture(),
			subscriberCountCaptor.capture(),
			subscribedByMeCaptor.capture(),
			contentCaptor.capture()
		);

		assertEquals(List.of(playlist1, playlist2), playlistCaptor.getAllValues());
		assertEquals(List.of(2L, 3L), subscriberCountCaptor.getAllValues());
		assertEquals(List.of(false, false), subscribedByMeCaptor.getAllValues());
		assertEquals(List.of(List.of(), List.of(contentSummary)), contentCaptor.getAllValues());
	}

	@Test
	@DisplayName("정렬 조건이 subscribeCount인 플레이리스트 목록 조회에 성공하면 플레이리스트 커서 페이지네이션 응답 DTO를 반환한다.")
	void findAllPlaylists_returnCursorResponse_whenSortBySubscribeCount() {
		// given
		UUID currentUserId = UUID.randomUUID();
		UUID playlist2OwnerId = UUID.randomUUID();
		UUID playlistId1 = UUID.randomUUID();
		UUID playlistId2 = UUID.randomUUID();
		UUID contentId = UUID.randomUUID();
		Instant updatedAt1 = Instant.parse("2026-06-24T01:00:00Z");
		Instant updatedAt2 = Instant.parse("2026-06-24T00:00:00Z");

		User currentUser = createUser(currentUserId);
		User playlist2Owner = createUser(playlist2OwnerId);
		Playlist playlist1 = createPlaylist(currentUser, playlistId1);
		Playlist playlist2 = createPlaylist(playlist2Owner, playlistId2);
		ReflectionTestUtils.setField(playlist1, "updatedAt", updatedAt1);
		ReflectionTestUtils.setField(playlist2, "updatedAt", updatedAt2);

		Content content = createContent(contentId);

		PlaylistSearchRequest request = new PlaylistSearchRequest(
			"제목",
			null, null, null, null,
			2,
			SortDirection.DESCENDING,
			PlaylistSortBy.subscribeCount
		);

		PlaylistRow playlistRow1 = new PlaylistRow(
			playlist1,
			2L,
			currentUserId,
			currentUser.getName(),
			currentUser.getProfileImageUrl()
		);
		PlaylistRow playlistRow2 = new PlaylistRow(
			playlist2,
			3L,
			playlist2OwnerId,
			playlist2Owner.getName(),
			playlist2Owner.getProfileImageUrl()
		);

		PlaylistCursorPage playlistCursorPage = new PlaylistCursorPage(
			List.of(playlistRow2, playlistRow1),
			true,
			3L
		);

		PlaylistContentRow playlistContentRow = new PlaylistContentRow(playlistId2, content);

		UserSummary userSummary1 = new UserSummary(
			currentUserId,
			currentUser.getName(),
			currentUser.getProfileImageUrl()
		);

		UserSummary userSummary2 = new UserSummary(
			playlist2OwnerId,
			playlist2Owner.getName(),
			playlist2Owner.getProfileImageUrl()
		);

		ContentSummary contentSummary = new ContentSummary(
			contentId,
			content.getType(),
			content.getTitle(),
			content.getDescription(),
			content.getThumbnailUrl(),
			List.of(),
			content.getAverageRating(),
			content.getReviewCount()
		);

		PlaylistDto playlistDto1 = new PlaylistDto(
			playlistId1,
			userSummary1,
			playlist1.getTitle(),
			playlist1.getDescription(),
			updatedAt1,
			2L,
			false,
			List.of()
		);

		PlaylistDto playlistDto2 = new PlaylistDto(
			playlistId2,
			userSummary2,
			playlist2.getTitle(),
			playlist2.getDescription(),
			updatedAt2,
			3L,
			true,
			List.of(contentSummary)
		);

		CursorResponsePlaylistDto cursorResponsePlaylistDtoResult = new CursorResponsePlaylistDto(
			List.of(playlistDto2, playlistDto1),
			playlistDto1.subscriberCount().toString(),
			playlist1.getId(),
			true,
			3L,
			PlaylistSortBy.subscribeCount.toString(),
			SortDirection.DESCENDING
		);

		when(userRepository.findById(currentUserId))
			.thenReturn(Optional.of(currentUser));
		when(playlistRepository.findAllPlaylists(request))
			.thenReturn(playlistCursorPage);
		when(playlistSubscriptionRepository.findSubscribedPlaylistIds(
			List.of(playlistId2, playlistId1),
			currentUserId)
		).thenReturn(Set.of(playlistId2));
		when(playlistContentRepository.findAllContentsByPlaylistIdsWithDeletedAtNull(
			List.of(playlistId2, playlistId1))
		).thenReturn(List.of(playlistContentRow));
		when(contentTagRepository.findTagNamesByContentIds(List.of(contentId)))
			.thenReturn(List.of());
		when(playlistMapper.toDto(
				any(Playlist.class),
				any(UserSummary.class),
				anyLong(),
				anyBoolean(),
				anyList()
			)
		).thenReturn(playlistDto2, playlistDto1);

		// when
		CursorResponsePlaylistDto result = playlistService.findAllPlaylists(request, currentUserId);

		// then
		assertEquals(cursorResponsePlaylistDtoResult, result);
		assertEquals(List.of(playlistDto2, playlistDto1), result.data());
		assertEquals(playlistDto1.subscriberCount().toString(), result.nextCursor());
		assertEquals(playlist1.getId(), result.nextIdAfter());
		assertEquals(true, result.hasNext());
		assertEquals(3L, result.totalCount());
		assertEquals(PlaylistSortBy.subscribeCount.toString(), result.sortBy());
		assertEquals(SortDirection.DESCENDING, result.sortDirection());

		verify(userRepository).findById(currentUserId);
		verify(playlistRepository).findAllPlaylists(request);
		verify(playlistSubscriptionRepository).findSubscribedPlaylistIds(
			List.of(playlistId2, playlistId1),
			currentUserId
		);
		verify(playlistContentRepository).findAllContentsByPlaylistIdsWithDeletedAtNull(
			List.of(playlistId2, playlistId1)
		);
		verify(contentTagRepository).findTagNamesByContentIds(List.of(contentId));

		ArgumentCaptor<Playlist> playlistCaptor = ArgumentCaptor.forClass(Playlist.class);
		ArgumentCaptor<UserSummary> ownerUserSummary = ArgumentCaptor.forClass(UserSummary.class);
		ArgumentCaptor<Long> subscriberCountCaptor = ArgumentCaptor.forClass(Long.class);
		ArgumentCaptor<Boolean> subscribedByMeCaptor = ArgumentCaptor.forClass(Boolean.class);
		ArgumentCaptor<List<ContentSummary>> contentCaptor = ArgumentCaptor.forClass(List.class);

		verify(playlistMapper, times(2)).toDto(
			playlistCaptor.capture(),
			ownerUserSummary.capture(),
			subscriberCountCaptor.capture(),
			subscribedByMeCaptor.capture(),
			contentCaptor.capture()
		);

		assertEquals(List.of(playlist2, playlist1), playlistCaptor.getAllValues());
		assertEquals(List.of(userSummary2, userSummary1), ownerUserSummary.getAllValues());
		assertEquals(List.of(3L, 2L), subscriberCountCaptor.getAllValues());
		assertEquals(List.of(true, false), subscribedByMeCaptor.getAllValues());
		assertEquals(List.of(List.of(contentSummary), List.of()), contentCaptor.getAllValues());
	}

	@Test
	@DisplayName("플레이리스트 목록 조회 시 데이터가 없다면 빈 리스트를 반환한다.")
	void findAllPlaylists_returnEmptyCursorResponse_whenValidRequest() {
		// given
		UUID currentUserId = UUID.randomUUID();

		User currentUser = createUser(currentUserId);

		PlaylistSearchRequest request = new PlaylistSearchRequest(
			"제목",
			null, null, null, null,
			2,
			SortDirection.DESCENDING,
			PlaylistSortBy.updatedAt
		);

		PlaylistCursorPage playlistCursorPage = new PlaylistCursorPage(
			List.of(),
			false,
			0L
		);

		when(userRepository.findById(currentUserId))
			.thenReturn(Optional.of(currentUser));
		when(playlistRepository.findAllPlaylists(request))
			.thenReturn(playlistCursorPage);

		// when
		CursorResponsePlaylistDto result = playlistService.findAllPlaylists(request, currentUserId);

		// then
		assertTrue(result.data().isEmpty());
		assertNull(result.nextCursor());
		assertNull(result.nextIdAfter());
		assertFalse(result.hasNext());
		assertEquals(0L, result.totalCount());
		assertEquals(PlaylistSortBy.updatedAt.toString(), result.sortBy());
		assertEquals(SortDirection.DESCENDING, result.sortDirection());

		verify(userRepository).findById(currentUserId);
		verify(playlistRepository).findAllPlaylists(request);
	}

	@Test
	@DisplayName("플레이리스트 목록 조회 시 사용자가 존재하지 않으면 예외가 발생한다.")
	void findAllPlaylists_throwException_whenUserNotFound() {
		// given
		UUID currentUserId = UUID.randomUUID();

		PlaylistSearchRequest request = new PlaylistSearchRequest(
			"제목",
			null, null, null, null,
			2,
			SortDirection.DESCENDING,
			PlaylistSortBy.updatedAt
		);

		when(userRepository.findById(currentUserId))
			.thenReturn(Optional.empty());

		// when, then
		// TODO: USER_NOT_FOUND 같은 사용자 커스텀 예외 추가 시 `IllegalArgumentException.class` 수정
		assertThrows(IllegalArgumentException.class,
			() -> playlistService.findAllPlaylists(request, currentUserId));

		verify(userRepository).findById(currentUserId);
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