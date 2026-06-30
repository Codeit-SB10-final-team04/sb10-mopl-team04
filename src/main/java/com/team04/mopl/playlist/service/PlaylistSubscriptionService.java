package com.team04.mopl.playlist.service;

import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team04.mopl.playlist.entity.Playlist;
import com.team04.mopl.playlist.entity.PlaylistSubscription;
import com.team04.mopl.playlist.exception.PlaylistErrorCode;
import com.team04.mopl.playlist.exception.PlaylistException;
import com.team04.mopl.playlist.repository.PlaylistRepository;
import com.team04.mopl.playlist.repository.PlaylistSubscriptionRepository;
import com.team04.mopl.user.entity.User;
import com.team04.mopl.user.exception.UserErrorCode;
import com.team04.mopl.user.exception.UserException;
import com.team04.mopl.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class PlaylistSubscriptionService {

	private final UserRepository userRepository;
	private final PlaylistRepository playlistRepository;
	private final PlaylistSubscriptionRepository playlistSubscriptionRepository;

	@Transactional
	public void subscribePlaylist(UUID playlistId, UUID currentUserId) {
		log.info("[PLAYLIST_SUBSCRIPTION] 플레이리스트 구독 시작: currentUserId={}, playlistId={}",
			currentUserId, playlistId);

		// 현재 로그인한 인증된 사용자 조회
		User user = getUserOrThrow(currentUserId);

		// 플레이리스트 조회
		Playlist playlist = getPlaylistOrThrow(playlistId);

		// 본인이 생성한 플레이리스트 여부
		if (isSelfSubscription(playlist.getOwner().getId(), currentUserId)) {
			// 본인이 생성한 플레이리스트에 구독 요청했으므로 예외 발생
			throw new PlaylistException(PlaylistErrorCode.PLAYLIST_SELF_SUBSCRIPTION_NOT_ALLOWED);
		}

		// 기존 구독 여부
		if (isSubscribed(playlistId, currentUserId)) {
			// 이미 구독했는데 또 구독 요청 시 예외 발생
			throw new PlaylistException(PlaylistErrorCode.PLAYLIST_ALREADY_SUBSCRIBED);
		}

		try {
			PlaylistSubscription playlistSubscription = PlaylistSubscription.builder()
				.subscriber(user)
				.playlist(playlist)
				.build();

			// 구독
			playlistSubscriptionRepository.saveAndFlush(playlistSubscription);
		} catch (DataIntegrityViolationException e) {
			// unique 제약 조건 충돌
			throw new PlaylistException(PlaylistErrorCode.PLAYLIST_ALREADY_SUBSCRIBED, e)
				.addDetail("playlistId", playlistId)
				.addDetail("currentUserId", currentUserId);
		}

		log.info("[PLAYLIST_SUBSCRIPTION] 플레이리스트 구독 완료: currentUserId={}, playlistId={}",
			currentUserId, playlistId);
	}

	@Transactional
	public void unSubscribePlaylist(UUID playlistId, UUID currentUserId) {
		log.info("[PLAYLIST_UNSUBSCRIPTION] 플레이리스트 구독 취소 시작: currentUserId={}, playlistId={}",
			currentUserId, playlistId);

		// 플레이리스트 조회
		Playlist playlist = getPlaylistOrThrow(playlistId);

		// 본인이 생성한 플레이리스트 여부
		if (isSelfSubscription(playlist.getOwner().getId(), currentUserId)) {
			// 본인이 생성한 플레이리스트에 구독 취소 요청했으므로 예외 발생
			throw new PlaylistException(PlaylistErrorCode.PLAYLIST_SELF_UNSUBSCRIPTION_NOT_ALLOWED);
		}

		// 플레이리스트 구독 관계 조회
		PlaylistSubscription playlistSubscription = getPlaylistSubscriptionOrThrow(playlistId, currentUserId);

		// 구독 취소
		playlistSubscriptionRepository.delete(playlistSubscription);

		log.info("[PLAYLIST_UNSUBSCRIPTION] 플레이리스트 구독 취소 완료: currentUserId={}, playlistId={}",
			currentUserId, playlistId);
	}

	// 사용자 조회
	private User getUserOrThrow(UUID userId) {
		return userRepository.findByIdAndLockedFalse(userId)
			.orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND)
				.addDetail("userId", userId));
	}

	// 삭제되지 않은 플레이리스트를 소유자 정보와 함께 조회
	private Playlist getPlaylistOrThrow(UUID playlistId) {
		// UserSummary를 만들기 위해서 owner 정보가 필요
		return playlistRepository.findByIdWithOwnerAndDeletedAtIsNull(playlistId)
			.orElseThrow(() -> new PlaylistException(PlaylistErrorCode.PLAYLIST_NOT_FOUND));
	}

	// 본인이 생성한 플레이리스트 여부
	private boolean isSelfSubscription(UUID ownerId, UUID currentUserId) {
		return ownerId.equals(currentUserId);
	}

	// 기존 구독 여부
	private boolean isSubscribed(UUID playlistId, UUID subscriberId) {
		return playlistSubscriptionRepository.existsByPlaylistIdAndSubscriberId(playlistId, subscriberId);
	}

	private PlaylistSubscription getPlaylistSubscriptionOrThrow(UUID playlistId, UUID subscriberId) {
		return playlistSubscriptionRepository.findByPlaylistIdAndSubscriberId(playlistId, subscriberId)
			.orElseThrow(() -> new PlaylistException(PlaylistErrorCode.PLAYLIST_UNSUBSCRIBED));
	}
}
