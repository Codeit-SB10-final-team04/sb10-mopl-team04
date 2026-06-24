package com.team04.mopl.playlist.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.team04.mopl.content.repository.ContentTagRepository;
import com.team04.mopl.playlist.dto.request.PlaylistCreateRequest;
import com.team04.mopl.playlist.dto.response.PlaylistDto;
import com.team04.mopl.playlist.entity.Playlist;
import com.team04.mopl.playlist.mapper.PlaylistMapper;
import com.team04.mopl.playlist.repository.PlaylistContentRepository;
import com.team04.mopl.playlist.repository.PlaylistRepository;
import com.team04.mopl.playlist.repository.PlaylistSubscriptionRepository;
import com.team04.mopl.user.dto.response.UserSummary;
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
		UserSummary ownerSummary = new UserSummary(owner.getId(), owner.getName(), owner.getProfileImageUrl());
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
				any(UserSummary.class),
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
			any(UserSummary.class),
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
			.thenThrow(new IllegalArgumentException());

		// when, then
		// TODO: USER_NOT_FOUND 같은 사용자 커스텀 예외 추가 시 `IllegalArgumentException.class` 수정
		assertThrows(IllegalArgumentException.class,
			() -> playlistService.createPlaylist(request, currentUserId));

		verify(userRepository).findById(any(UUID.class));
		verify(playlistRepository, never()).save(any(Playlist.class));
		verify(playlistMapper, never()).toDto(
			any(Playlist.class),
			any(UserSummary.class),
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
}