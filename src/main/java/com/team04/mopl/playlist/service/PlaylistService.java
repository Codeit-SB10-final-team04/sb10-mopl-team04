package com.team04.mopl.playlist.service;

import static java.util.stream.Collectors.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team04.mopl.content.entity.Content;
import com.team04.mopl.content.repository.ContentTagRepository;
import com.team04.mopl.playlist.dto.request.PlaylistCreateRequest;
import com.team04.mopl.playlist.dto.response.PlaylistContentSummary;
import com.team04.mopl.playlist.dto.response.PlaylistDto;
import com.team04.mopl.playlist.dto.response.PlaylistUserSummary;
import com.team04.mopl.playlist.dto.row.PlaylistContentRow;
import com.team04.mopl.playlist.entity.Playlist;
import com.team04.mopl.playlist.exception.PlaylistErrorCode;
import com.team04.mopl.playlist.exception.PlaylistException;
import com.team04.mopl.playlist.mapper.PlaylistMapper;
import com.team04.mopl.playlist.repository.PlaylistContentRepository;
import com.team04.mopl.playlist.repository.PlaylistRepository;
import com.team04.mopl.playlist.repository.PlaylistSubscriptionRepository;
import com.team04.mopl.user.entity.User;
import com.team04.mopl.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlaylistService {

	private final UserRepository userRepository;
	private final ContentTagRepository contentTagRepository;
	private final PlaylistRepository playlistRepository;
	private final PlaylistSubscriptionRepository playlistSubscriptionRepository;
	private final PlaylistContentRepository playlistContentRepository;
	private final PlaylistMapper playlistMapper;

	@Transactional
	public PlaylistDto createPlaylist(PlaylistCreateRequest request, UUID currentUserId) {
		log.info("[PLAYLIST_CREATE] 플레이리스트 생성 시작: currentUserId={}, title={}",
			currentUserId, request.title());

		// 현재 로그인한 인증된 사용자 조회
		User owner = getUserOrThrow(currentUserId);

		// 플레이리스트 생성
		Playlist playlist = Playlist.builder()
			.owner(owner)
			.title(request.title())
			.description(request.description())
			.build();

		// 플레이리스트 저장
		playlistRepository.save(playlist);

		// 플레이리스트 소유자 summary
		// TODO: UserSummary 구현 후 변경
		// UserSummary ownerSummary = getUserSummary(owner);
		PlaylistUserSummary ownerSummary = getUserSummary(owner);
		// 플레이리스트 구독자 조회 (생성이라 존재 X)
		long subscriberCount = 0L;
		// 플레이리스트 구독 여부 조회 (생성이라 존재 X)
		boolean subscribedByMe = false;
		// 플레이리스트 내 콘텐츠 조회 (생성이라 존재 X)
		// TODO: ContentSummary 구현 후 변경
		// List<ContentSummary> contentSummaries = List.of();
		List<PlaylistContentSummary> contentSummaries = List.of();

		PlaylistDto playlistDto = playlistMapper.toDto(
			playlist,
			ownerSummary,
			subscriberCount,
			subscribedByMe,
			contentSummaries
		);

		log.info("[PLAYLIST_CREATE] 플레이리스트 생성 완료: currentUserId={}, playlistId={}, title={}",
			currentUserId, playlist.getId(), playlist.getTitle());

		return playlistDto;
	}

	@Transactional(readOnly = true)
	public PlaylistDto findPlaylist(UUID playlistId, UUID currentUserId) {
		log.debug("[PLAYLIST_FIND] 플레이리스트 단건 조회 시작: currentUserId={}, playlistId={}",
			currentUserId, playlistId);

		// 현재 로그인한 인증된 사용자 조회
		getUserOrThrow(currentUserId);

		// 플레이리스트 조회
		Playlist playlist = getPlaylistOrThrow(playlistId);

		// 플레이리스트 소유자 summary
		// TODO: UserSummary 구현 후 변경
		// UserSummary ownerSummary = getUserSummary(owner);
		PlaylistUserSummary ownerSummary = getUserSummary(playlist.getOwner());

		// 플레이리스트 구독자 조회
		long subscriberCount = getSubscriberCount(playlistId);

		// 플레이리스트 구독 여부 조회
		boolean subscribedByMe = isSubscribedByMe(playlistId, currentUserId);

		// 플레이리스트 내 콘텐츠 조회
		// TODO: ContentSummary 구현 후 변경
		// List<ContentSummary> contentSummaries = List.of();
		List<PlaylistContentSummary> contentSummaries = getContentSummaries(playlistId);

		PlaylistDto playlistDto = playlistMapper.toDto(
			playlist,
			ownerSummary,
			subscriberCount,
			subscribedByMe,
			contentSummaries
		);

		log.debug("[PLAYLIST_FIND] 플레이리스트 단건 조회 완료: currentUserId={}, playlistId={}, title={}, description={}",
			currentUserId, playlist.getId(), playlist.getTitle(), playlist.getDescription());

		return playlistDto;
	}

	// 사용자 조회
	// TODO: `USER_NOT_FOUND` 같은 사용자 커스텀 예외 추가 시 `.orElseThrow(...)` 교체
	private User getUserOrThrow(UUID userId) {
		return userRepository.findById(userId)
			.orElseThrow(() -> new IllegalArgumentException("User not found!"));
	}

	// TODO: UserSummary 구현 후 변경
	// private UserSummary getUserSummary(User user) {
	private PlaylistUserSummary getUserSummary(User user) {
		// 	return new UserSummary(
		return new PlaylistUserSummary(
			user.getId(),
			user.getName(),
			user.getProfileImageUrl()
		);
	}

	// 플레이리스트 조회
	private Playlist getPlaylistOrThrow(UUID playlistId) {
		return playlistRepository.findByIdWithOwnerAndDeletedAtIsNull(playlistId)
			.orElseThrow(() -> new PlaylistException(PlaylistErrorCode.PLAYLIST_NOT_FOUND));
	}

	private Map<UUID, Long> getSubscriberCountsByPlaylistIds(List<UUID> playlistIds) {
		if (playlistIds.isEmpty()) {
			return Map.of();
		}

		return playlistSubscriptionRepository.countAllSubscribersByPlaylistIds(playlistIds)
			.stream()
			.collect(
				toMap(
					row -> row.playlistId(),
					row -> row.subscriberCount()
				)
			);
	}

	// SubscriberCount 단건 조회
	private long getSubscriberCount(UUID playlistId) {
		return getSubscriberCountsByPlaylistIds(List.of(playlistId))
			.getOrDefault(playlistId, 0L);
	}

	// 현재 사용자가 구독한 플레이리스트 id 목록 조회
	private Set<UUID> getSubscribedPlaylistIds(List<UUID> playlistIds, UUID currentUserId) {
		if (playlistIds.isEmpty()) {
			return Set.of();
		}

		return playlistSubscriptionRepository.findSubscribedPlaylistIds(playlistIds, currentUserId);
	}

	// 단건 조회에서 현재 사용자가 플레이리스트 구독 여부 조회
	private boolean isSubscribedByMe(UUID playlistId, UUID currentUserId) {
		return getSubscribedPlaylistIds(List.of(playlistId), currentUserId)
			.contains(playlistId);
	}

	// 플레이리스트 id별(플레이리스트 내에 존재하는) 콘텐츠 리스트 조회
	// TODO: ContentSummary 구현 시 교체
	// private Map<UUID, List<ContentSummary>> getContentSummariesByPlaylistIds(List<UUID> playlistIds) {
	private Map<UUID, List<PlaylistContentSummary>> getContentSummariesByPlaylistIds(List<UUID> playlistIds) {
		if (playlistIds.isEmpty()) {
			return Map.of();
		}

		List<PlaylistContentRow> rows = playlistContentRepository.findAllContentsByPlaylistIds(playlistIds);
		List<UUID> contentIds = rows.stream()
			.map(row -> row.content().getId())
			.distinct()
			.toList();

		if (contentIds.isEmpty()) {
			return Map.of();
		}

		// contentId에 따른 tagName 조회
		Map<UUID, List<String>> tagNameMap = contentTagRepository
			.findTagNamesByContentIds(contentIds)
			.stream()
			.collect(groupingBy(
					tagRow -> tagRow.contentId(),
					mapping(tagRow -> tagRow.tagName(), toList())
				)
			);

		return rows.stream()
			.collect(groupingBy(
					row -> row.playlistId(),
					mapping(row -> toContentSummary(row.content(), tagNameMap), toList())
				)
			);
	}

	// TODO: ContentSummary 구현 시 교체
	// private ContentSummary toContentSummary(Content content, Map<UUID, List<String>> tagNameMap) {
	private List<PlaylistContentSummary> getContentSummaries(UUID playlistId) {
		return getContentSummariesByPlaylistIds(List.of(playlistId))
			.getOrDefault(playlistId, List.of());
	}

	// TODO: ContentSummary 구현 시 교체
	// private ContentSummary toContentSummary(Content content, Map<UUID, List<String>> tagNameMap) {
	private PlaylistContentSummary toContentSummary(Content content, Map<UUID, List<String>> tagNameMap) {
		return new PlaylistContentSummary(
			content.getId(),
			content.getType(),
			content.getTitle(),
			content.getDescription(),
			content.getThumbnailUrl(),
			tagNameMap.getOrDefault(content.getId(), List.of()),
			content.getAverageRating(),
			content.getReviewerCount()
		);
	}
}
