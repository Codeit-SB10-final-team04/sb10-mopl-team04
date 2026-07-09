package com.team04.mopl.playlist.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team04.mopl.content.entity.Content;
import com.team04.mopl.content.exception.ContentErrorCode;
import com.team04.mopl.content.exception.ContentException;
import com.team04.mopl.content.repository.ContentRepository;
import com.team04.mopl.playlist.entity.Playlist;
import com.team04.mopl.playlist.entity.PlaylistContent;
import com.team04.mopl.playlist.event.PlaylistContentAddedEvent;
import com.team04.mopl.playlist.exception.PlaylistErrorCode;
import com.team04.mopl.playlist.exception.PlaylistException;
import com.team04.mopl.playlist.repository.PlaylistContentRepository;
import com.team04.mopl.playlist.repository.PlaylistRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class PlaylistContentService {

	private final ContentRepository contentRepository;
	private final PlaylistRepository playlistRepository;
	private final PlaylistContentRepository playlistContentRepository;

	private final ApplicationEventPublisher applicationEventPublisher;

	@PreAuthorize("@playlistAuthorizationEvaluator.isOwner(#playlistId, authentication.principal)")
	@Transactional
	public void addContentToPlaylist(UUID playlistId, UUID contentId, UUID currentUserId) {
		log.info("[PLAYLIST_CONTENT_ADD] 플레이리스트에 콘텐츠 추가 시작: currentUserId={}, playlistId={}, contentId={}",
			currentUserId, playlistId, contentId);

		// 플레이리스트 조회
		Playlist playlist = getPlaylistOrThrow(playlistId);

		// 콘텐츠 조회
		Content content = getContentOrThrow(contentId);

		// 플레이리스트 내에 이미 존재하는 콘텐츠인지 확인
		if (existsContentInPlaylist(playlistId, contentId)) {
			throw new PlaylistException(PlaylistErrorCode.PLAYLIST_CONTENT_ALREADY_ADD);
		}

		// unique 제약 조건에 대비한 try...catch 문 -> saveAndFlush
		try {
			PlaylistContent playlistContent = PlaylistContent.builder()
				.playlist(playlist)
				.content(content)
				.build();

			// 콘텐츠 추가
			playlistContentRepository.saveAndFlush(playlistContent);

			// 플레이리스트 updatedAt 갱신
			playlist.touchUpdatedAt(Instant.now());

			// 이벤트 발행
			applicationEventPublisher.publishEvent(
				PlaylistContentAddedEvent.of(
					playlist.getId(),
					playlist.getTitle(),
					playlist.getOwner().getId(),
					content.getId(),
					content.getTitle()
				)
			);

		} catch (DataIntegrityViolationException e) {
			// unique 제약 조건 충돌
			throw new PlaylistException(PlaylistErrorCode.PLAYLIST_CONTENT_ALREADY_ADD, e)
				.addDetail("playlistId", playlistId)
				.addDetail("contentId", contentId);
		}

		log.info("[PLAYLIST_CONTENT_ADD] 플레이리스트에 콘텐츠 추가 완료: currentUserId={}, playlistId={}, contentId={}",
			currentUserId, playlistId, contentId);
	}

	@PreAuthorize("@playlistAuthorizationEvaluator.isOwner(#playlistId, authentication.principal)")
	@Transactional
	public void deleteContentFromPlaylist(UUID playlistId, UUID contentId, UUID currentUserId) {
		log.info("[PLAYLIST_CONTENT_DELETE] 플레이리스트에 콘텐츠 삭제 시작: currentUserId={}, playlistId={}, contentId={}",
			currentUserId, playlistId, contentId);

		// 플레이리스트 조회
		Playlist playlist = getPlaylistOrThrow(playlistId);

		// 플레이리스트 내에 존재하는 콘텐츠인지 확인
		PlaylistContent playlistContent = getPlaylistContentOrThrow(playlistId, contentId);

		// 삭제
		playlistContentRepository.delete(playlistContent);

		// 플레이리스트 updatedAt 갱신
		playlist.touchUpdatedAt(Instant.now());

		log.info("[PLAYLIST_CONTENT_DELETE] 플레이리스트에 콘텐츠 삭제 완료: currentUserId={}, playlistId={}, contentId={}",
			currentUserId, playlistId, contentId);
	}

	// 삭제되지 않은 플레이리스트를 소유자 정보와 함께 조회
	private Playlist getPlaylistOrThrow(UUID playlistId) {
		return playlistRepository.findByIdWithOwnerAndDeletedAtIsNull(playlistId)
			.orElseThrow(() -> new PlaylistException(PlaylistErrorCode.PLAYLIST_NOT_FOUND));
	}

	// 삭제되지 않은 콘텐츠 조회
	private Content getContentOrThrow(UUID contentId) {
		return contentRepository.findByIdAndDeletedAtIsNull(contentId)
			.orElseThrow(() -> new ContentException(ContentErrorCode.CONTENT_NOT_FOUND));
	}

	// 이미 등록된 콘텐츠 여부 확인
	private boolean existsContentInPlaylist(UUID playlistId, UUID contentId) {
		return playlistContentRepository.existsByPlaylistIdAndContentId(playlistId, contentId);
	}

	private PlaylistContent getPlaylistContentOrThrow(UUID playlistId, UUID contentId) {
		return playlistContentRepository
			.findByPlaylistIdAndContentId(playlistId, contentId)
			.orElseThrow(() -> new PlaylistException(PlaylistErrorCode.PLAYLIST_CONTENT_NOT_FOUND));
	}
}
