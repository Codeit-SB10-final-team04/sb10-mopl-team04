package com.team04.mopl.watching.service;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team04.mopl.common.dto.ContentSummary;
import com.team04.mopl.common.dto.CursorResponse;
import com.team04.mopl.common.dto.UserSummary;
import com.team04.mopl.common.enums.SortDirection;
import com.team04.mopl.content.entity.Content;
import com.team04.mopl.content.exception.ContentErrorCode;
import com.team04.mopl.content.exception.ContentException;
import com.team04.mopl.content.repository.ContentRepository;
import com.team04.mopl.user.entity.User;
import com.team04.mopl.user.exception.UserErrorCode;
import com.team04.mopl.user.exception.UserException;
import com.team04.mopl.user.repository.UserRepository;
import com.team04.mopl.watching.dto.request.WatchingSessionPageRequest;
import com.team04.mopl.watching.dto.response.WatchingSessionChange;
import com.team04.mopl.watching.dto.response.WatchingSessionDto;
import com.team04.mopl.watching.enums.ChangeType;
import com.team04.mopl.watching.store.WatchingSessionInfo;
import com.team04.mopl.watching.store.WatchingSessionStore;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// 시청 세션 비즈니스 로직 서비스
// watcherCount는 DB가 아닌 인메모리 Store(WatchingSessionStore)에서 집계
// 시청 세션은 WebSocket 연결과 수명이 같은 일시적 데이터이므로 DB 영속화 불필요
// ContentDto 반환 시 인메모리 watcherCount를 합쳐서 내려줌 (ContentMapper 참고)
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WatchingSessionService {

	private static final long DISCONNECT_GRACE_PERIOD_SECONDS = 3;

	private final WatchingSessionStore watchingSessionStore;
	private final UserRepository userRepository;
	private final ContentRepository contentRepository;
	private final ScheduledExecutorService disconnectScheduler = Executors.newSingleThreadScheduledExecutor();
	private final ConcurrentHashMap<String, ScheduledFuture<?>> pendingDisconnects = new ConcurrentHashMap<>();

	// 시청 세션 입장 (sessionId로 멀티탭 참조 카운팅)
	// 첫 탭이면 JOIN 브로드캐스트, 추가 탭이면 empty
	// 재연결(새로고침) 시 대기 중인 퇴장 예약이 있으면 취소
	public Optional<WatchingSessionChange> join(UUID contentId, UUID userId, String sessionId) {
		log.info("[WATCHING_SESSION_JOIN] 시청 세션 입장 시작: contentId={}, userId={}, sessionId={}",
			contentId, userId, sessionId);

		cancelPendingDisconnect(contentId, userId);

		User user = getUserOrThrow(userId);
		Content content = getContentOrThrow(contentId);

		Optional<WatchingSessionInfo> added = watchingSessionStore.addWatcher(contentId, userId, sessionId);

		if (added.isEmpty()) {
			log.debug("[WATCHING_SESSION_JOIN] 추가 탭 입장 (브로드캐스트 없음): contentId={}, userId={}",
				contentId, userId);
			return Optional.empty();
		}

		WatchingSessionChange change = createChange(ChangeType.JOIN, user, content, added.get());

		log.info("[WATCHING_SESSION_JOIN] 첫 탭 입장 완료: contentId={}, userId={}, watcherCount={}",
			contentId, userId, change.watcherCount());

		return Optional.of(change);
	}

	// 시청 세션 퇴장 (sessionId로 멀티탭 참조 카운팅)
	// 마지막 탭이면 LEAVE 브로드캐스트, 아직 남았으면 empty
	public Optional<WatchingSessionChange> leave(UUID contentId, UUID userId, String sessionId) {
		log.info("[WATCHING_SESSION_LEAVE] 시청 세션 퇴장 시작: contentId={}, userId={}, sessionId={}",
			contentId, userId, sessionId);

		User user = getUserOrThrow(userId);
		Content content = getContentOrThrow(contentId);

		Optional<WatchingSessionInfo> removed = watchingSessionStore.removeWatcher(contentId, userId, sessionId);

		if (removed.isEmpty()) {
			log.debug("[WATCHING_SESSION_LEAVE] 탭 닫힘 (아직 시청 중): contentId={}, userId={}", contentId, userId);
			return Optional.empty();
		}

		WatchingSessionChange change = createChange(ChangeType.LEAVE, user, content, removed.get());

		log.info("[WATCHING_SESSION_LEAVE] 마지막 탭 퇴장 완료: contentId={}, userId={}, watcherCount={}",
			contentId, userId, change.watcherCount());

		return Optional.of(change);
	}

	// DISCONNECT 시 즉시 퇴장하지 않고 일정 시간 대기 후 강제 퇴장 (재연결 대비)
	// 강제 퇴장: sessionId 상관없이 해당 유저의 모든 세션을 정리 (좀비 세션 방지)
	public void scheduleLeave(UUID contentId, UUID userId, String sessionId,
		java.util.function.Consumer<WatchingSessionChange> onLeave) {

		String key = toDisconnectKey(contentId, userId);

		log.debug("[WATCHING_SESSION_DISCONNECT] 퇴장 예약: contentId={}, userId={}, {}초 후 실행",
			contentId, userId, DISCONNECT_GRACE_PERIOD_SECONDS);

		ScheduledFuture<?> future = disconnectScheduler.schedule(() -> {
			try {
				forceLeave(contentId, userId)
					.ifPresent(onLeave);
			} catch (Exception e) {
				log.error("[WATCHING_SESSION_DISCONNECT] 지연 퇴장 실패: contentId={}, userId={}",
					contentId, userId, e);
			} finally {
				pendingDisconnects.remove(key);
			}
		}, DISCONNECT_GRACE_PERIOD_SECONDS, TimeUnit.SECONDS);

		ScheduledFuture<?> prev = pendingDisconnects.put(key, future);
		if (prev != null) {
			prev.cancel(false);
		}
	}

	// 강제 퇴장: sessionId 무관하게 해당 유저의 모든 세션 정리
	private Optional<WatchingSessionChange> forceLeave(UUID contentId, UUID userId) {
		log.info("[WATCHING_SESSION_FORCE_LEAVE] 강제 퇴장: contentId={}, userId={}", contentId, userId);

		User user = getUserOrThrow(userId);
		Content content = getContentOrThrow(contentId);

		Optional<WatchingSessionInfo> removed = watchingSessionStore.forceRemoveWatcher(contentId, userId);

		if (removed.isEmpty()) {
			log.debug("[WATCHING_SESSION_FORCE_LEAVE] 이미 퇴장 상태: contentId={}, userId={}", contentId, userId);
			return Optional.empty();
		}

		WatchingSessionChange change = createChange(ChangeType.LEAVE, user, content, removed.get());

		log.info("[WATCHING_SESSION_FORCE_LEAVE] 강제 퇴장 완료: contentId={}, userId={}, watcherCount={}",
			contentId, userId, change.watcherCount());

		return Optional.of(change);
	}

	private void cancelPendingDisconnect(UUID contentId, UUID userId) {
		String key = toDisconnectKey(contentId, userId);
		ScheduledFuture<?> pending = pendingDisconnects.remove(key);
		if (pending != null) {
			pending.cancel(false);
			log.info("[WATCHING_SESSION_JOIN] 퇴장 예약 취소 (재연결 감지): contentId={}, userId={}",
				contentId, userId);
		}
	}

	private String toDisconnectKey(UUID contentId, UUID userId) {
		return contentId + ":" + userId;
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
		log.debug("[WATCHING_SESSION_FIND_BY_CONTENT] 시청 세션 목록 조회 시작: contentId={}", contentId);

		List<WatchingSessionDto> sessions = getAllSessionsByContentId(contentId);

		// 시청자 이름 필터
		if (request.watcherNameLike() != null && !request.watcherNameLike().isBlank()) {
			sessions = sessions.stream()
				.filter(s -> s.watcher().name().contains(request.watcherNameLike()))
				.toList();
		}

		long totalCount = sessions.size();

		// createdAt(입장 시각) 기준 정렬, 동일 시각은 id로 tie-break
		boolean ascending = request.sortDirection() == SortDirection.ASCENDING;
		Comparator<WatchingSessionDto> comparator = Comparator
			.comparing(WatchingSessionDto::createdAt)
			.thenComparing(dto -> dto.id().toString());

		if (!ascending) {
			comparator = comparator.reversed();
		}

		sessions = sessions.stream().sorted(comparator).toList();

		// (createdAt, id) 복합 커서 필터: 커서 이후의 요소만 남김
		if (request.cursor() != null && request.idAfter() != null) {
			Instant cursorCreatedAt = parseCursor(request.cursor());

			if (cursorCreatedAt != null) {
				String cursorId = request.idAfter().toString();

				sessions = sessions.stream()
					.filter(s -> isAfterCursor(s, cursorCreatedAt, cursorId, ascending))
					.toList();
			}
		}

		List<WatchingSessionDto> paged = sessions.stream()
			.limit(request.limit())
			.toList();

		boolean hasNext = sessions.size() > request.limit();
		String nextCursor = null;
		String nextIdAfter = null;

		if (hasNext && !paged.isEmpty()) {
			WatchingSessionDto last = paged.get(paged.size() - 1);
			nextCursor = last.createdAt().toString();
			nextIdAfter = last.id().toString();
		}

		log.debug("[WATCHING_SESSION_FIND_BY_CONTENT] 시청 세션 목록 조회 완료: contentId={}, totalCount={}",
			contentId, totalCount);

		return new CursorResponse<>(
			paged, nextCursor, nextIdAfter,
			hasNext, totalCount,
			request.sortBy().name(), request.sortDirection().name()
		);
	}

	// 특정 사용자의 시청 세션 조회 (nullable), 여러 콘텐츠 시청 중이면 가장 최근 입장 세션 반환
	public Optional<WatchingSessionDto> findByWatcherId(UUID watcherId) {
		log.debug("[WATCHING_SESSION_FIND_BY_WATCHER] 유저 시청 세션 조회 시작: watcherId={}", watcherId);

		Map<UUID, WatchingSessionInfo> watchingSessions = watchingSessionStore.getWatchingSessions(watcherId);

		if (watchingSessions.isEmpty()) {
			return Optional.empty();
		}

		// 가장 최근에 입장한 세션 선택
		Map.Entry<UUID, WatchingSessionInfo> latest = watchingSessions.entrySet().stream()
			.max(Comparator.comparing(entry -> entry.getValue().joinedAt()))
			.orElseThrow();

		UUID contentId = latest.getKey();
		WatchingSessionInfo info = latest.getValue();

		User user = getUserOrThrow(watcherId);
		Content content = getContentOrThrow(contentId);

		return Optional.of(new WatchingSessionDto(
			info.id(),
			info.joinedAt(),
			new UserSummary(user.getId(), user.getName(), user.getProfileImageUrl()),
			toContentSummary(content)
		));
	}

	// 시청자 수 조회
	public long getWatcherCount(UUID contentId) {
		return watchingSessionStore.getWatcherCount(contentId);
	}

	// 특정 콘텐츠의 전체 시청 세션 목록 조회 (Store에 저장된 id/joinedAt 사용)
	private List<WatchingSessionDto> getAllSessionsByContentId(UUID contentId) {
		Map<UUID, WatchingSessionInfo> watchers = watchingSessionStore.getWatchers(contentId);

		Content content = getContentOrThrow(contentId);
		ContentSummary contentSummary = toContentSummary(content);

		List<User> users = userRepository.findAllByIdInAndLockedFalse(watchers.keySet());

		return users.stream()
			.map(user -> {
				WatchingSessionInfo info = watchers.get(user.getId());

				return new WatchingSessionDto(
					info.id(),
					info.joinedAt(),
					new UserSummary(user.getId(), user.getName(), user.getProfileImageUrl()),
					contentSummary
				);
			})
			.toList();
	}

	// WatchingSessionChange 생성 (join/leave에서 이미 검증된 엔티티와 Store 세션 정보 사용)
	private WatchingSessionChange createChange(
		ChangeType type,
		User user,
		Content content,
		WatchingSessionInfo info
	) {
		UserSummary watcher = new UserSummary(user.getId(), user.getName(), user.getProfileImageUrl());

		// 시청자 수는 인메모리 Store에서 집계
		long watcherCount = watchingSessionStore.getWatcherCount(content.getId());

		WatchingSessionDto sessionDto = new WatchingSessionDto(
			info.id(),
			info.joinedAt(),
			watcher,
			toContentSummary(content)
		);

		return new WatchingSessionChange(type, sessionDto, watcherCount);
	}

	private User getUserOrThrow(UUID userId) {
		return userRepository.findByIdAndLockedFalse(userId)
			.orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND)
				.addDetail("userId", userId));
	}

	private Content getContentOrThrow(UUID contentId) {
		return contentRepository.findById(contentId)
			.orElseThrow(() -> new ContentException(ContentErrorCode.CONTENT_NOT_FOUND)
				.addDetail("contentId", contentId));
	}

	private ContentSummary toContentSummary(Content content) {
		return new ContentSummary(
			content.getId(),
			content.getType(),
			content.getTitle(),
			content.getDescription(),
			content.getThumbnailUrl(),
			List.of(),
			content.getAverageRating(),
			content.getReviewCount()
		);
	}

	// 커서(createdAt 문자열) 파싱, 실패 시 null 반환 (첫 페이지로 처리)
	private Instant parseCursor(String cursor) {
		try {
			return Instant.parse(cursor);
		} catch (DateTimeParseException e) {
			log.warn("[WATCHING_SESSION_CURSOR_PARSE_FAILED] 커서 파싱 실패, 첫 페이지로 처리: cursor={}", cursor);
			return null;
		}
	}

	// (createdAt, id) 복합 비교로 커서 이후 요소인지 판단
	private boolean isAfterCursor(
		WatchingSessionDto session,
		Instant cursorCreatedAt,
		String cursorId,
		boolean ascending
	) {
		int createdAtCompare = session.createdAt().compareTo(cursorCreatedAt);

		if (createdAtCompare == 0) {
			int idCompare = session.id().toString().compareTo(cursorId);
			return ascending ? idCompare > 0 : idCompare < 0;
		}

		return ascending ? createdAtCompare > 0 : createdAtCompare < 0;
	}
}
