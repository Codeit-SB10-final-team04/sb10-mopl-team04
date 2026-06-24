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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.team04.mopl.content.entity.Content;
import com.team04.mopl.content.entity.ContentType;
import com.team04.mopl.content.repository.ContentTagRepository;
import com.team04.mopl.playlist.dto.request.PlaylistCreateRequest;
import com.team04.mopl.playlist.dto.response.PlaylistContentSummary;
import com.team04.mopl.playlist.dto.response.PlaylistDto;
import com.team04.mopl.playlist.dto.response.PlaylistUserSummary;
import com.team04.mopl.playlist.dto.row.PlaylistContentRow;
import com.team04.mopl.playlist.dto.row.PlaylistSubscriberCountRow;
import com.team04.mopl.playlist.entity.Playlist;
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

		PlaylistDto expectedDto = new PlaylistDto(
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
		).thenReturn(expectedDto);

		// when
		PlaylistDto result = playlistService.createPlaylist(request, currentUserId);

		// then
		assertEquals(expectedDto, result);
		assertEquals(expectedDto.title(), result.title());
		assertEquals(expectedDto.description(), result.description());

		verify(userRepository).findById(any(UUID.class));
		verify(playlistRepository).save(any(Playlist.class));
		verify(playlistMapper).toDto(
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

		verify(userRepository).findById(any(UUID.class));
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

		PlaylistDto expectedDto = new PlaylistDto(
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
		when(playlistContentRepository.findAllContentsByPlaylistIds(List.of(playlistId)))
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
		).thenReturn(expectedDto);

		// when
		PlaylistDto result = playlistService.findPlaylist(playlistId, currentUserId);

		// then
		assertEquals(expectedDto, result);
		assertEquals(expectedDto.title(), result.title());
		assertEquals(expectedDto.description(), result.description());
		assertEquals(expectedDto.updatedAt(), result.updatedAt());
		assertEquals(expectedDto.subscriberCount(), result.subscriberCount());
		assertEquals(expectedDto.subscribedByMe(), result.subscribedByMe());
		assertEquals(expectedDto.contents().size(), result.contents().size());

		verify(userRepository).findById(any(UUID.class));
		verify(playlistRepository).findByIdWithOwnerAndDeletedAtIsNull(any(UUID.class));
		verify(playlistSubscriptionRepository).countAllSubscribersByPlaylistIds(anyList());
		verify(playlistSubscriptionRepository).findSubscribedPlaylistIds(anyList(), any(UUID.class));
		verify(playlistContentRepository).findAllContentsByPlaylistIds(anyList());
		verify(playlistMapper).toDto(
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

		verify(userRepository).findById(any(UUID.class));
		verify(playlistRepository, never()).findByIdWithOwnerAndDeletedAtIsNull(any(UUID.class));
		verify(playlistSubscriptionRepository, never()).countAllSubscribersByPlaylistIds(anyList());
		verify(playlistSubscriptionRepository, never()).findSubscribedPlaylistIds(anyList(), any(UUID.class));
		verify(playlistContentRepository, never()).findAllContentsByPlaylistIds(anyList());
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
		UUID ownerId = UUID.randomUUID();
		UUID playlistId = UUID.randomUUID();

		User currentUser = createUser(currentUserId);
		User owner = createUser(ownerId);
		Playlist playlist = createPlaylist(owner, playlistId);

		// TODO: USER_NOT_FOUND 같은 사용자 커스텀 예외 추가 시 `IllegalArgumentException.class` 수정
		when(userRepository.findById(currentUserId))
			.thenReturn(Optional.of(currentUser));
		when(playlistRepository.findByIdWithOwnerAndDeletedAtIsNull(playlistId))
			.thenReturn(Optional.empty());

		// when, then
		assertThrows(PlaylistException.class,
			() -> playlistService.findPlaylist(playlistId, currentUserId));

		verify(userRepository).findById(any(UUID.class));
		verify(playlistRepository).findByIdWithOwnerAndDeletedAtIsNull(any(UUID.class));
		verify(playlistSubscriptionRepository, never()).countAllSubscribersByPlaylistIds(anyList());
		verify(playlistSubscriptionRepository, never()).findSubscribedPlaylistIds(anyList(), any(UUID.class));
		verify(playlistContentRepository, never()).findAllContentsByPlaylistIds(anyList());
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