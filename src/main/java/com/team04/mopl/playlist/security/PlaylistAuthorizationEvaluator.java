package com.team04.mopl.playlist.security;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.team04.mopl.auth.security.MoplUserDetails;
import com.team04.mopl.playlist.entity.Playlist;
import com.team04.mopl.playlist.exception.PlaylistErrorCode;
import com.team04.mopl.playlist.exception.PlaylistException;
import com.team04.mopl.playlist.repository.PlaylistRepository;

import lombok.RequiredArgsConstructor;

// 플레이리스트 권한 판단 클래스
@Component
@RequiredArgsConstructor
public class PlaylistAuthorizationEvaluator {

	private final PlaylistRepository playlistRepository;

	// 플레이리스트 소유자 여부 확인
	public boolean isOwner(UUID playlistId, MoplUserDetails moplUserDetails) {
		if (playlistId == null) {
			throw new PlaylistException(PlaylistErrorCode.PLAYLIST_INVALID_INPUT)
				.addDetail("isPlaylistIdProvided", false);
		}

		// 인증된 사용자 null 여부
		if (moplUserDetails == null) {
			return false;
		}

		// 플레이리스트 조회
		Playlist playlist = getPlaylistOrThrow(playlistId);

		return playlist.getOwner().getId().equals(moplUserDetails.getUserId());
	}

	// 삭제되지 않은 플레이리스트를 소유자 정보와 함께 조회
	private Playlist getPlaylistOrThrow(UUID playlistId) {
		// UserSummary를 만들기 위해서 owner 정보가 필요
		return playlistRepository.findByIdWithOwnerAndDeletedAtIsNull(playlistId)
			.orElseThrow(() -> new PlaylistException(PlaylistErrorCode.PLAYLIST_NOT_FOUND));
	}
}
