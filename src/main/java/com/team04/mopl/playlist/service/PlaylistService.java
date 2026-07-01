package com.team04.mopl.playlist.service;

import static java.util.stream.Collectors.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team04.mopl.common.dto.ContentSummary;
import com.team04.mopl.common.dto.UserSummary;
import com.team04.mopl.common.enums.SortDirection;
import com.team04.mopl.content.entity.Content;
import com.team04.mopl.content.repository.ContentTagRepository;
import com.team04.mopl.playlist.dto.request.PlaylistCreateRequest;
import com.team04.mopl.playlist.dto.request.PlaylistSearchRequest;
import com.team04.mopl.playlist.dto.request.PlaylistUpdateRequest;
import com.team04.mopl.playlist.dto.response.CursorResponsePlaylistDto;
import com.team04.mopl.playlist.dto.response.PlaylistCursorPageDto;
import com.team04.mopl.playlist.dto.response.PlaylistDto;
import com.team04.mopl.playlist.dto.row.PlaylistContentRow;
import com.team04.mopl.playlist.entity.Playlist;
import com.team04.mopl.playlist.enums.PlaylistSortBy;
import com.team04.mopl.playlist.exception.PlaylistErrorCode;
import com.team04.mopl.playlist.exception.PlaylistException;
import com.team04.mopl.playlist.mapper.PlaylistMapper;
import com.team04.mopl.playlist.repository.PlaylistContentRepository;
import com.team04.mopl.playlist.repository.PlaylistRepository;
import com.team04.mopl.playlist.repository.PlaylistSubscriptionRepository;
import com.team04.mopl.user.entity.User;
import com.team04.mopl.user.exception.UserErrorCode;
import com.team04.mopl.user.exception.UserException;
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
		UserSummary ownerSummary = getUserSummary(owner);

		// 플레이리스트 구독자 조회 (생성이라 존재 X)
		long subscriberCount = 0L;

		// 플레이리스트 구독 여부 조회 (생성이라 존재 X)
		boolean subscribedByMe = false;

		// 플레이리스트 내 콘텐츠 조회 (생성이라 존재 X)
		List<ContentSummary> contentSummaries = List.of();

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

		// 삭제되지 않은 플레이리스트를 소유자 정보와 함께 조회
		Playlist playlist = getPlaylistOrThrow(playlistId);

		// PlaylistDto 생성
		PlaylistDto playlistDto = toPlaylistDto(playlist, currentUserId);

		log.debug("[PLAYLIST_FIND] 플레이리스트 단건 조회 완료: currentUserId={}, playlistId={}, title={}, description={}",
			currentUserId, playlist.getId(), playlist.getTitle(), playlist.getDescription());

		return playlistDto;
	}

	@Transactional(readOnly = true)
	public CursorResponsePlaylistDto findAllPlaylists(
		PlaylistSearchRequest request,
		UUID currentUserId
	) {
		int limit = request.limit();
		SortDirection sortDirection = request.sortDirection();
		PlaylistSortBy sortBy = request.sortBy();

		log.debug(
			"[PLAYLIST_FIND_ALL] 플레이리스트 목록 조회 시작: keyword={}, ownerIdEqual={}, subscriberIdEqual={}, cursor={}, idAfter={}, limit={}, sortDirection={}, sortBy={}",
			request.normalizedKeyword(), request.ownerIdEqual(), request.subscriberIdEqual(), request.cursor(),
			request.idAfter(), limit, sortDirection, sortBy);

		// 조건에 따라 플레이리스트 목록 조회
		// 정렬 조건에 구독자 수 포함 -> 플레이리스트 조회 시 구독자 수도 같이 조회
		PlaylistCursorPageDto playlistCursorPageDto = playlistRepository.findAllPlaylists(request);

		List<UUID> playlistIds = playlistCursorPageDto.playlistRows().stream()
			.map(row -> row.playlist().getId())
			.toList();

		// 조회된 플레이리스트 중 현재 사용자가 구독 중인 플레이리스트 id Set 생성
		Set<UUID> subscribedPlaylistIds = getSubscribedPlaylistIds(playlistIds, currentUserId);

		// 플레이리스트가 가진 콘텐츠의 ContentSummaryMap 생성
		Map<UUID, List<ContentSummary>> contentSummaryMap =
			getContentSummariesByPlaylistIds(playlistIds);

		// PlaylistDto로 조립 (OwnerSummary, 구독 여부, ContentSummary)
		List<PlaylistDto> playlistDtoList = playlistCursorPageDto.playlistRows().stream()
			.map(row -> {
				Playlist playlist = row.playlist();
				UUID playlistId = playlist.getId();
				return playlistMapper.toDto(
					playlist,
					new UserSummary(row.ownerId(), row.ownerName(), row.ownerProfileImageUrl()),
					row.subscriberCount(),
					subscribedPlaylistIds.contains(playlistId),
					contentSummaryMap.getOrDefault(playlistId, List.of())
				);
			})
			.toList();

		// 조회된 플레이리스트 수
		int playlistDtoListSize = playlistDtoList.size();
		boolean hasNext = playlistCursorPageDto.hasNext();

		// 마지막 요소
		PlaylistDto lastPlaylistDto = playlistDtoList.isEmpty()
			? null
			: playlistDtoList.get(playlistDtoListSize - 1);
		// 다음 cursor
		String nextCursor = hasNext && lastPlaylistDto != null
			? resolveNextCursor(lastPlaylistDto, sortBy)
			: null;
		// 다음 보조 cursor (idAfter)
		UUID nextIdAfter = hasNext && lastPlaylistDto != null
			? lastPlaylistDto.id()
			: null;

		// Cursor 페이지네이션으로 만들기
		CursorResponsePlaylistDto cursorResponsePlaylistDto
			= new CursorResponsePlaylistDto(
			playlistDtoList,
			nextCursor,
			nextIdAfter,
			hasNext,
			playlistCursorPageDto.totalCount(),
			sortBy.toString(),
			sortDirection
		);

		log.debug("[PLAYLIST_FIND_ALL] 플레이리스트 목록 조회 완료: size={}, nextCursor={}, nextIdAfter={}",
			playlistDtoListSize, nextCursor, nextIdAfter);

		return cursorResponsePlaylistDto;
	}

	@PreAuthorize("@playlistAuthorizationEvaluator.isOwner(#playlistId, authentication.principal)")
	@Transactional
	public PlaylistDto updatePlaylist(
		UUID playlistId,
		PlaylistUpdateRequest request,
		UUID currentUserId
	) {
		String requestTitle = request.title();
		String requestDescription = request.description();

		log.info(
			"[PLAYLIST_UPDATE] 플레이리스트 수정 시작: currentUserId={}, playlistId={}, requestTitle={}, requestDescription={}",
			currentUserId, playlistId, requestTitle, requestDescription);

		// title과 description이 `isBlank` 인지 확인
		validateNotBlank("title", requestTitle);
		validateNotBlank("description", requestDescription);

		// 플레이리스트 조회
		Playlist playlist = getPlaylistOrThrow(playlistId);

		// 요청과 현재의 title과 description을 비교하고,
		// 같으면 `null` / 다르면 요청값
		String normalizedTitle = normalizeString(
			requestTitle,
			playlist.getTitle()
		);
		String normalizedDescription = normalizeString(
			requestDescription,
			playlist.getDescription()
		);

		// 전부 `null` 일 경우(입력값 X 거나 전부 현재값과 동일할 경우)
		validateAllRequestExistingOrNull(normalizedTitle, normalizedDescription);

		// Playlist 변경사항 수정
		playlist.update(normalizedTitle, normalizedDescription);

		// PlaylistDto 생성
		PlaylistDto playlistDto = toPlaylistDto(playlist, currentUserId);

		log.info("[PLAYLIST_UPDATE] 플레이리스트 수정 완료: currentUserId={}, playlistId={}, title={}, description={}",
			currentUserId, playlist.getId(), playlist.getTitle(), playlist.getDescription());

		return playlistDto;
	}

	@PreAuthorize("@playlistAuthorizationEvaluator.isOwner(#playlistId, authentication.principal)")
	@Transactional
	public void softDeletePlaylist(UUID playlistId, UUID currentUserId) {
		log.info("[PLAYLIST_SOFT_DELETE] 플레이리스트 논리 삭제 시작: currentUserId={}, playlistId={}",
			currentUserId, playlistId);

		// 플레이리스트 조회
		Playlist playlist = getPlaylistOrThrow(playlistId);

		// 논리 삭제
		playlist.markDeleted(Instant.now());

		log.info("[PLAYLIST_SOFT_DELETE] 플레이리스트 논리 삭제 완료: currentUserId={}, playlistId={}",
			currentUserId, playlistId);
	}

	// 사용자 조회
	private User getUserOrThrow(UUID userId) {
		return userRepository.findById(userId)
			.orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND)
				.addDetail("userId", userId));
	}

	// 플레이리스트 응답에 포함할 소유자 요약 정보를 만듦
	private UserSummary getUserSummary(User user) {
		// 	return new UserSummary(
		return new UserSummary(
			user.getId(),
			user.getName(),
			user.getProfileImageUrl()
		);
	}

	// 삭제되지 않은 플레이리스트를 소유자 정보와 함께 조회
	private Playlist getPlaylistOrThrow(UUID playlistId) {
		// UserSummary를 만들기 위해서 owner 정보가 필요
		return playlistRepository.findByIdWithOwnerAndDeletedAtIsNull(playlistId)
			.orElseThrow(() -> new PlaylistException(PlaylistErrorCode.PLAYLIST_NOT_FOUND));
	}

	// 여러 플레이리스트의 구독자 수를 한 번에 조회한 후, playlistId 기준 Map으로 변환
	private Map<UUID, Long> getSubscriberCountsByPlaylistIds(List<UUID> playlistIds) {
		// 조회 대상이 없으면 실행 X
		if (playlistIds.isEmpty()) {
			return Map.of();
		}

		// 목록 조회에서도 사용할 수 있도록 playlistId별 구독자 수 Map을 만듦
		return playlistSubscriptionRepository.countAllSubscribersByPlaylistIds(playlistIds)
			.stream()
			.collect(
				toMap(
					row -> row.playlistId(),
					row -> row.subscriberCount()
				)
			);
	}

	// 단건 조회에서 사용할 구독자 수 조회
	private long getSubscriberCount(UUID playlistId) {
		// 구독자가 없을 경우 기본값 0 반환
		return getSubscriberCountsByPlaylistIds(List.of(playlistId))
			.getOrDefault(playlistId, 0L);
	}

	// 현재 사용자가 구독 중인 플레이리스트 id 조회
	private Set<UUID> getSubscribedPlaylistIds(List<UUID> playlistIds, UUID currentUserId) {
		// 조회 대상이 없다면 실행 X
		if (playlistIds.isEmpty()) {
			return Set.of();
		}

		return playlistSubscriptionRepository.findSubscribedPlaylistIds(playlistIds, currentUserId);
	}

	// 현재의 사용자가 해당 플레이리스트를 구독했는지 확인
	private boolean isSubscribedByMe(UUID playlistId, UUID currentUserId) {
		return getSubscribedPlaylistIds(List.of(playlistId), currentUserId)
			.contains(playlistId);
	}

	// 여러 플레이리스트에 포함된 콘텐츠 조회 후, 콘텐츠별 태그를 붙여 요약 DTO 구현
	private Map<UUID, List<ContentSummary>> getContentSummariesByPlaylistIds(List<UUID> playlistIds) {
		// 조회 대상이 없다면 실행 X
		if (playlistIds.isEmpty()) {
			return Map.of();
		}

		// 여러 플레이리스트에 포함된 콘텐츠 일괄 조회
		List<PlaylistContentRow> rows = playlistContentRepository.findAllContentsByPlaylistIdsWithDeletedAtNull(
			playlistIds);

		// 태그 조회에 필요한 콘텐츠 id만 중복 없이 추출
		List<UUID> contentIds = rows.stream()
			.map(row -> row.content().getId())
			.distinct()
			.toList();

		// 포함된 콘텐츠가 없다면 빈 결과 반환
		if (contentIds.isEmpty()) {
			return Map.of();
		}

		// 콘텐츠별 태그명을 한 번에 조회 후 콘텐츠 id 기준으로 그룹화
		Map<UUID, List<String>> tagNameMap = contentTagRepository
			.findTagNamesByContentIds(contentIds)
			.stream()
			.collect(groupingBy(
					tagRow -> tagRow.contentId(),
					mapping(tagRow -> tagRow.tagName(), toList())
				)
			);

		// 플레이리스트 id별로 콘텐츠 요약 정보를 묶음
		return rows.stream()
			.collect(groupingBy(
					row -> row.playlistId(),
					mapping(row -> toContentSummary(row.content(), tagNameMap), toList())
				)
			);
	}

	private String resolveNextCursor(PlaylistDto lastPlaylistDto, PlaylistSortBy sortBy) {
		return sortBy.equals(PlaylistSortBy.updatedAt)
			? lastPlaylistDto.updatedAt().toString()
			: lastPlaylistDto.subscriberCount().toString();
	}

	// 단건 조회에서 사용할 콘텐츠 요약 목록을 가져옴
	private List<ContentSummary> getContentSummaries(UUID playlistId) {
		// 포함된 콘텐츠가 없다면 빈 목록 반환
		return getContentSummariesByPlaylistIds(List.of(playlistId))
			.getOrDefault(playlistId, List.of());
	}

	// 콘텐츠와 태그 목록을 이용해 콘텐츠 요약 DTO 생성
	private ContentSummary toContentSummary(Content content, Map<UUID, List<String>> tagNameMap) {
		return new ContentSummary(
			content.getId(),
			content.getType(),
			content.getTitle(),
			content.getDescription(),
			content.getThumbnailUrl(),
			tagNameMap.getOrDefault(content.getId(), List.of()),
			content.getAverageRating(),
			content.getReviewCount()
		);
	}

	// 플레이리스트 구독자 수, 구독 여부, 콘텐츠 조회 후 PlaylistDto 조립 메서드
	private PlaylistDto toPlaylistDto(Playlist playlist, UUID currentUserId) {
		UUID playlistId = playlist.getId();

		// 플레이리스트 소유자 summary
		UserSummary ownerSummary = getUserSummary(playlist.getOwner());

		// 플레이리스트 구독자 조회
		long subscriberCount = getSubscriberCount(playlistId);

		// 플레이리스트 구독 여부 조회
		boolean subscribedByMe = isSubscribedByMe(playlistId, currentUserId);

		// 플레이리스트 내 콘텐츠 조회
		List<ContentSummary> contentSummaries = getContentSummaries(playlistId);

		return playlistMapper.toDto(
			playlist,
			ownerSummary,
			subscriberCount,
			subscribedByMe,
			contentSummaries
		);
	}

	private String normalizeString(String requestValue, String currentValue) {
		if (requestValue == null) {
			return null;
		}

		// 좌/우 공백 제거
		requestValue = requestValue.strip();

		return requestValue.equals(currentValue)
			? null
			: requestValue;
	}

	// ===== 검증(validate) 메서드 =====
	// blank가 아님을 검증
	private void validateNotBlank(String fieldName, String value) {
		if (value != null && value.isBlank()) {
			throw new PlaylistException(PlaylistErrorCode.PLAYLIST_INVALID_INPUT)
				.addDetail(fieldName, value);
		}
	}

	private void validateAllRequestExistingOrNull(String normalizedTitle, String normalizedDescription) {
		if (normalizedTitle == null && normalizedDescription == null) {
			throw new PlaylistException(PlaylistErrorCode.PLAYLIST_NO_CHANGE_VALUE);
		}
	}
}
