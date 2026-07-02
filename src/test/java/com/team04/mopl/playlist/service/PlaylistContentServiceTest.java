package com.team04.mopl.playlist.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import com.team04.mopl.content.entity.Content;
import com.team04.mopl.content.entity.ContentType;
import com.team04.mopl.content.exception.ContentException;
import com.team04.mopl.content.repository.ContentRepository;
import com.team04.mopl.playlist.entity.Playlist;
import com.team04.mopl.playlist.entity.PlaylistContent;
import com.team04.mopl.playlist.event.PlaylistContentAddedEvent;
import com.team04.mopl.playlist.exception.PlaylistException;
import com.team04.mopl.playlist.repository.PlaylistContentRepository;
import com.team04.mopl.playlist.repository.PlaylistRepository;
import com.team04.mopl.user.entity.User;

@ExtendWith(MockitoExtension.class)
class PlaylistContentServiceTest {

	@Mock
	private ContentRepository contentRepository;

	@Mock
	private PlaylistRepository playlistRepository;

	@Mock
	private PlaylistContentRepository playlistContentRepository;

	@Mock
	private ApplicationEventPublisher applicationEventPublisher;

	@InjectMocks
	private PlaylistContentService playlistContentService;

	@Test
	@DisplayName("플레이리스트 내 콘텐츠 추가 요청에 성공하면 플레이리스트 콘텐츠 관계를 저장한다.")
	void addContentToPlaylist_savePlaylistContent_whenValidRequest() {
		// given
		UUID playlistId = UUID.randomUUID();
		UUID contentId = UUID.randomUUID();
		UUID currentUserId = UUID.randomUUID();

		User currentUser = createUser(currentUserId);
		Playlist playlist = createPlaylist(currentUser, playlistId);
		Content content = createContent(contentId);

		Instant oldUpdatedAt = playlist.getUpdatedAt();

		when(playlistRepository.findByIdWithOwnerAndDeletedAtIsNull(playlistId))
			.thenReturn(Optional.of(playlist));
		when(contentRepository.findByIdAndDeletedAtIsNull(contentId))
			.thenReturn(Optional.of(content));
		when(playlistContentRepository.existsByPlaylistIdAndContentId(playlistId, contentId))
			.thenReturn(Boolean.FALSE);

		// when
		playlistContentService.addContentToPlaylist(playlistId, contentId, currentUserId);

		// then
		verify(playlistRepository).findByIdWithOwnerAndDeletedAtIsNull(playlistId);
		verify(contentRepository).findByIdAndDeletedAtIsNull(contentId);
		verify(playlistContentRepository).existsByPlaylistIdAndContentId(playlistId, contentId);
		verify(applicationEventPublisher).publishEvent(any(PlaylistContentAddedEvent.class));

		ArgumentCaptor<PlaylistContent> playlistContentCaptor =
			ArgumentCaptor.forClass(PlaylistContent.class);
		verify(playlistContentRepository).saveAndFlush(playlistContentCaptor.capture());

		Instant nowUpdatedAt = playlistContentCaptor.getValue().getPlaylist().getUpdatedAt();
		assertNotEquals(oldUpdatedAt, nowUpdatedAt);
	}

	@Test
	@DisplayName("존재하지 않는 플레이리스트로 플레이리스트 내 콘텐츠를 추가하면 예외가 발생한다.")
	void addContentToPlaylist_throwException_whenPlaylistNotFound() {
		// given
		UUID playlistId = UUID.randomUUID();
		UUID contentId = UUID.randomUUID();
		UUID currentUserId = UUID.randomUUID();

		when(playlistRepository.findByIdWithOwnerAndDeletedAtIsNull(playlistId))
			.thenReturn(Optional.empty());

		// when
		assertThrows(PlaylistException.class,
			() -> playlistContentService.addContentToPlaylist(playlistId, contentId, currentUserId));

		// then
		verify(playlistRepository).findByIdWithOwnerAndDeletedAtIsNull(playlistId);
		verify(contentRepository, never()).findByIdAndDeletedAtIsNull(any(UUID.class));
		verify(playlistContentRepository, never()).existsByPlaylistIdAndContentId(any(UUID.class), any(UUID.class));
		verify(playlistContentRepository, never()).saveAndFlush(any(PlaylistContent.class));
		verify(applicationEventPublisher, never()).publishEvent(any(PlaylistContentAddedEvent.class));
	}

	@Test
	@DisplayName("존재하지 않는 콘텐츠로 플레이리스트 내 콘텐츠를 추가하면 예외가 발생한다.")
	void addContentToPlaylist_throwException_whenContentNotFound() {
		// given
		UUID playlistId = UUID.randomUUID();
		UUID contentId = UUID.randomUUID();
		UUID currentUserId = UUID.randomUUID();

		User currentUser = createUser(currentUserId);
		Playlist playlist = createPlaylist(currentUser, playlistId);

		when(playlistRepository.findByIdWithOwnerAndDeletedAtIsNull(playlistId))
			.thenReturn(Optional.of(playlist));
		when(contentRepository.findByIdAndDeletedAtIsNull(contentId))
			.thenReturn(Optional.empty());

		// when
		assertThrows(ContentException.class,
			() -> playlistContentService.addContentToPlaylist(playlistId, contentId, currentUserId));

		// then
		verify(playlistRepository).findByIdWithOwnerAndDeletedAtIsNull(playlistId);
		verify(contentRepository).findByIdAndDeletedAtIsNull(contentId);
		verify(playlistContentRepository, never()).existsByPlaylistIdAndContentId(any(UUID.class), any(UUID.class));
		verify(playlistContentRepository, never()).saveAndFlush(any(PlaylistContent.class));
		verify(applicationEventPublisher, never()).publishEvent(any(PlaylistContentAddedEvent.class));
	}

	@Test
	@DisplayName("이미 플레이리스트 내 추가된 콘텐츠가 중복 추가될 시 예외가 발생한다.")
	void addContentToPlaylist_throwException_whenDuplicateAddContent() {
		// given
		UUID playlistId = UUID.randomUUID();
		UUID contentId = UUID.randomUUID();
		UUID currentUserId = UUID.randomUUID();

		User currentUser = createUser(currentUserId);
		Playlist playlist = createPlaylist(currentUser, playlistId);
		Content content = createContent(contentId);

		when(playlistRepository.findByIdWithOwnerAndDeletedAtIsNull(playlistId))
			.thenReturn(Optional.of(playlist));
		when(contentRepository.findByIdAndDeletedAtIsNull(contentId))
			.thenReturn(Optional.of(content));
		when(playlistContentRepository.existsByPlaylistIdAndContentId(playlistId, contentId))
			.thenReturn(Boolean.TRUE);

		// when
		assertThrows(PlaylistException.class,
			() -> playlistContentService.addContentToPlaylist(playlistId, contentId, currentUserId));

		// then
		verify(playlistRepository).findByIdWithOwnerAndDeletedAtIsNull(playlistId);
		verify(contentRepository).findByIdAndDeletedAtIsNull(contentId);
		verify(playlistContentRepository).existsByPlaylistIdAndContentId(playlistId, contentId);
		verify(playlistContentRepository, never()).saveAndFlush(any(PlaylistContent.class));
		verify(applicationEventPublisher, never()).publishEvent(any(PlaylistContentAddedEvent.class));
	}

	@Test
	@DisplayName("이미 플레이리스트 내 추가된 콘텐츠가 중복 추가될 시 예외가 발생한다.")
	void addContentToPlaylist_throwException_whenDuplicateAndConflictAddContent() {
		// given
		UUID playlistId = UUID.randomUUID();
		UUID contentId = UUID.randomUUID();
		UUID currentUserId = UUID.randomUUID();

		User currentUser = createUser(currentUserId);
		Playlist playlist = createPlaylist(currentUser, playlistId);
		Content content = createContent(contentId);

		when(playlistRepository.findByIdWithOwnerAndDeletedAtIsNull(playlistId))
			.thenReturn(Optional.of(playlist));
		when(contentRepository.findByIdAndDeletedAtIsNull(contentId))
			.thenReturn(Optional.of(content));
		when(playlistContentRepository.existsByPlaylistIdAndContentId(playlistId, contentId))
			.thenReturn(Boolean.FALSE);
		when(playlistContentRepository.saveAndFlush(any(PlaylistContent.class)))
			.thenThrow(new DataIntegrityViolationException("duplicate! conflict!"));

		// when
		assertThrows(PlaylistException.class,
			() -> playlistContentService.addContentToPlaylist(playlistId, contentId, currentUserId));

		// then
		verify(playlistRepository).findByIdWithOwnerAndDeletedAtIsNull(playlistId);
		verify(contentRepository).findByIdAndDeletedAtIsNull(contentId);
		verify(playlistContentRepository).existsByPlaylistIdAndContentId(any(UUID.class), any(UUID.class));
		verify(playlistContentRepository).saveAndFlush(any(PlaylistContent.class));
		verify(applicationEventPublisher, never()).publishEvent(any(PlaylistContentAddedEvent.class));
	}

	@Test
	@DisplayName("플레이리스트 내 콘텐츠 삭제 요청에 성공하면 플레이리스트 콘텐츠 관계를 삭제한다.")
	void deleteContentToPlaylist_savePlaylistContent_whenValidRequest() {
		// given
		UUID playlistId = UUID.randomUUID();
		UUID contentId = UUID.randomUUID();
		UUID currentUserId = UUID.randomUUID();

		User currentUser = createUser(currentUserId);
		Playlist playlist = createPlaylist(currentUser, playlistId);
		Content content = createContent(contentId);
		PlaylistContent playlistContent = createPlaylistContent(playlist, content);

		Instant oldUpdatedAt = playlist.getUpdatedAt();

		when(playlistRepository.findByIdWithOwnerAndDeletedAtIsNull(playlistId))
			.thenReturn(Optional.of(playlist));
		when(playlistContentRepository.findByPlaylistIdAndContentId(playlistId, contentId))
			.thenReturn(Optional.of(playlistContent));

		// when
		playlistContentService.deleteContentFromPlaylist(playlistId, contentId, currentUserId);

		// then
		verify(playlistRepository).findByIdWithOwnerAndDeletedAtIsNull(playlistId);
		verify(playlistContentRepository).findByPlaylistIdAndContentId(playlistId, contentId);

		ArgumentCaptor<PlaylistContent> playlistContentCaptor =
			ArgumentCaptor.forClass(PlaylistContent.class);
		verify(playlistContentRepository).delete(playlistContentCaptor.capture());

		Instant nowUpdatedAt = playlistContentCaptor.getValue().getPlaylist().getUpdatedAt();
		assertNotEquals(oldUpdatedAt, nowUpdatedAt);
	}

	@Test
	@DisplayName("존재하지 않는 플레이리스트로 플레이리스트 내 콘텐츠를 삭제하면 예외가 발생한다.")
	void deleteContentToPlaylist_throwException_whenPlaylistNotFound() {
		// given
		UUID playlistId = UUID.randomUUID();
		UUID contentId = UUID.randomUUID();
		UUID currentUserId = UUID.randomUUID();

		when(playlistRepository.findByIdWithOwnerAndDeletedAtIsNull(playlistId))
			.thenReturn(Optional.empty());

		// when
		assertThrows(PlaylistException.class,
			() -> playlistContentService.deleteContentFromPlaylist(playlistId, contentId, currentUserId));

		// then
		verify(playlistRepository).findByIdWithOwnerAndDeletedAtIsNull(playlistId);
		verify(playlistContentRepository, never()).findByPlaylistIdAndContentId(any(UUID.class), any(UUID.class));
		verify(playlistContentRepository, never()).delete(any(PlaylistContent.class));
	}

	@Test
	@DisplayName("플레이리스트 내에 존재하지 않은 콘텐츠 삭제 시 예외가 발생한다.")
	void deleteContentToPlaylist_throwException_whenPlaylistContentNotFound() {
		// given
		UUID playlistId = UUID.randomUUID();
		UUID contentId = UUID.randomUUID();
		UUID currentUserId = UUID.randomUUID();

		User currentUser = createUser(currentUserId);
		Playlist playlist = createPlaylist(currentUser, playlistId);

		when(playlistRepository.findByIdWithOwnerAndDeletedAtIsNull(playlistId))
			.thenReturn(Optional.of(playlist));
		when(playlistContentRepository.findByPlaylistIdAndContentId(playlistId, contentId))
			.thenReturn(Optional.empty());

		// when
		assertThrows(PlaylistException.class,
			() -> playlistContentService.deleteContentFromPlaylist(playlistId, contentId, currentUserId));

		// then
		verify(playlistRepository).findByIdWithOwnerAndDeletedAtIsNull(playlistId);
		verify(playlistContentRepository).findByPlaylistIdAndContentId(playlistId, contentId);
		verify(playlistContentRepository, never()).delete(any(PlaylistContent.class));
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

	private PlaylistContent createPlaylistContent(Playlist playlist, Content content) {
		PlaylistContent playlistContent = PlaylistContent.builder()
			.playlist(playlist)
			.content(content)
			.build();
		ReflectionTestUtils.setField(playlistContent, "id", UUID.randomUUID());
		return playlistContent;
	}

}