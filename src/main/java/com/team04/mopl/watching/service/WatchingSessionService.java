package com.team04.mopl.watching.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team04.mopl.common.dto.ContentSummary;
import com.team04.mopl.common.dto.CursorResponse;
import com.team04.mopl.common.dto.UserSummary;
import com.team04.mopl.content.entity.Content;
import com.team04.mopl.content.repository.ContentRepository;
import com.team04.mopl.user.entity.User;
import com.team04.mopl.user.exception.UserErrorCode;
import com.team04.mopl.user.exception.UserException;
import com.team04.mopl.user.repository.UserRepository;
import com.team04.mopl.watching.dto.request.WatchingSessionPageRequest;
import com.team04.mopl.watching.dto.response.WatchingSessionChange;
import com.team04.mopl.watching.dto.response.WatchingSessionDto;
import com.team04.mopl.watching.enums.ChangeType;
import com.team04.mopl.watching.store.WatchingSessionStore;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// 시청 세션 비즈니스 로직 서비스
// watcherCount는 DB가 아닌 인메모리 Store(WatchingSessionStore)에서 집계
// 시청 세션은 WebSocket 연결과 수명이 같은 일시적 데이터이므로 DB 영속화 불필요
// ContentDto 반환 시 인메모리 watcherCount를 합쳐서 내려줌 (ContentMapper 참고)
// TODO: 다중 서버(스케일 아웃) 환경에서는 WatchingSessionStore를 Redis로 교체 필요
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WatchingSessionService {

	private final WatchingSessionStore watchingSessionStore;
	private final UserRepository userRepository;
	private final ContentRepository contentRepository;

	// 시청 세션 입장 처리, 이미 시청 중이면 empty 반환
	public Optional<WatchingSessionChange> join(UUID contentId, UUID userId) {
		boolean added = watchingSessionStore.addWatcher(contentId, userId);

		if (!added) {
			log.debug("이미 시청 중인 사용자: contentId={}, userId={}", contentId, userId);
			return Optional.empty();
		}

		WatchingSessionChange change = createChange(ChangeType.JOIN, contentId, userId);

		log.debug("시청 세션 입장: contentId={}, userId={}, watcherCount={}",
			contentId, userId, change.watcherCount());

		return Optional.of(change);
	}

	// 시청 세션 퇴장 처리, 시청 중이 아니었으면 empty 반환
	public Optional<WatchingSessionChange> leave(UUID contentId, UUID userId) {
		boolean removed = watchingSessionStore.removeWatcher(contentId, userId);

		if (!removed) {
			log.debug("시청 중이 아닌 사용자: contentId={}, userId={}", contentId, userId);
			return Optional.empty();
		}

		WatchingSessionChange change = createChange(ChangeType.LEAVE, contentId, userId);

		log.debug("시청 세션 퇴장: contentId={}, userId={}, watcherCount={}",
			contentId, userId, change.watcherCount());

		return Optional.of(change);
	}

	// 특정 유저가 시청 중인 모든 contentId 조회
	public Set<UUID> getWatchingContentIds(UUID userId) {
		return watchingSessionStore.getWatchingContentIds(userId);
	}

	// 특정 콘텐츠를 시청 중인지 확인
	public boolean isWatching(UUID contentId, UUID userId) {
		return watchingSessionStore.isWatching(contentId, userId);
	}

	// 특정 콘텐츠의 시청 세션 목록 조회 (커서 페이지네이션)
	public CursorResponse<WatchingSessionDto> findByContentId(UUID contentId, WatchingSessionPageRequest request) {
		List<WatchingSessionDto> sessions = getAllSessionsByContentId(contentId);

		// 시청자 이름 필터
		if (request.watcherNameLike() != null && !request.watcherNameLike().isBlank()) {
			sessions = sessions.stream()
				.filter(s -> s.watcher().name().contains(request.watcherNameLike()))
				.toList();
		}

		long totalCount = sessions.size();

		// 커서 기반 페이지네이션
		int startIndex = 0;
		if (request.idAfter() != null) {
			for (int i = 0; i < sessions.size(); i++) {
				if (sessions.get(i).id().equals(request.idAfter())) {
					startIndex = i + 1;
					break;
				}
			}
		}

		List<WatchingSessionDto> paged = sessions.stream()
			.skip(startIndex)
			.limit(request.limit())
			.toList();

		boolean hasNext = startIndex + request.limit() < sessions.size();
		String nextCursor = null;
		String nextIdAfter = null;

		if (hasNext && !paged.isEmpty()) {
			WatchingSessionDto last = paged.get(paged.size() - 1);
			nextCursor = last.createdAt().toString();
			nextIdAfter = last.id().toString();
		}

		return new CursorResponse<>(
			paged, nextCursor, nextIdAfter,
			hasNext, totalCount,
			request.sortBy().name(), request.sortDirection().name()
		);
	}

	// 특정 콘텐츠의 전체 시청 세션 목록 조회
	private List<WatchingSessionDto> getAllSessionsByContentId(UUID contentId) {
		Set<UUID> watcherIds = watchingSessionStore.getWatchers(contentId);

		Content content = contentRepository.findById(contentId)
			.orElseThrow(() -> new IllegalArgumentException("콘텐츠를 찾을 수 없습니다."));

		ContentSummary contentSummary = toContentSummary(content);

		List<User> users = userRepository.findAllByIdInAndLockedFalse(watcherIds);

		return users.stream()
			.map(user -> new WatchingSessionDto(
				UUID.randomUUID(),
				Instant.now(),
				new UserSummary(user.getId(), user.getName(), user.getProfileImageUrl()),
				contentSummary
			))
			.toList();
	}

	// 특정 사용자의 시청 세션 조회 (nullable)
	public Optional<WatchingSessionDto> findByWatcherId(UUID watcherId) {
		Set<UUID> contentIds = watchingSessionStore.getWatchingContentIds(watcherId);

		if (contentIds.isEmpty()) {
			return Optional.empty();
		}

		UUID contentId = contentIds.iterator().next();

		User user = userRepository.findByIdAndLockedFalse(watcherId)
			.orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND)
				.addDetail("userId", watcherId));

		Content content = contentRepository.findById(contentId)
			.orElseThrow(() -> new IllegalArgumentException("콘텐츠를 찾을 수 없습니다."));

		return Optional.of(new WatchingSessionDto(
			UUID.randomUUID(),
			Instant.now(),
			new UserSummary(user.getId(), user.getName(), user.getProfileImageUrl()),
			toContentSummary(content)
		));
	}

	// 시청자 수 조회
	public long getWatcherCount(UUID contentId) {
		return watchingSessionStore.getWatcherCount(contentId);
	}

	private ContentSummary toContentSummary(Content content) {
		return new ContentSummary(
			content.getId(),
			content.getType(),
			content.getTitle(),
			content.getDescription(),
			content.getThumbnailUrl(),
			null,
			content.getAverageRating(),
			null
		);
	}

	// WatchingSessionChange 생성
	private WatchingSessionChange createChange(ChangeType type, UUID contentId, UUID userId) {
		User user = userRepository.findByIdAndLockedFalse(userId)
			.orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND)
				.addDetail("userId", userId));

		Content content = contentRepository.findById(contentId)
			.orElseThrow(() -> new IllegalArgumentException("콘텐츠를 찾을 수 없습니다."));

		UserSummary watcher = new UserSummary(user.getId(), user.getName(), user.getProfileImageUrl());

		// 시청자 수는 인메모리 Store에서 집계
		long watcherCount = watchingSessionStore.getWatcherCount(contentId);

		WatchingSessionDto sessionDto = new WatchingSessionDto(
			UUID.randomUUID(),
			Instant.now(),
			watcher,
			toContentSummary(content)
		);

		return new WatchingSessionChange(type, sessionDto, watcherCount);
	}
}
