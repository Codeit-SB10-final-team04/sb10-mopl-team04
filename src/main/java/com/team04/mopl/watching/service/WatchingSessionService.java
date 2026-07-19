package com.team04.mopl.watching.service;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PreDestroy;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.simp.user.SimpUserRegistry;
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
import com.team04.mopl.watching.event.WatchingSessionEvent;
import com.team04.mopl.watching.store.WatchingSessionStore;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Transactional(readOnly = true)
public class WatchingSessionService {

	private static final long JOIN_BROADCAST_RETRY_INTERVAL_MS = 50;
	private static final int JOIN_BROADCAST_MAX_RETRIES = 10;

	private final WatchingSessionStore watchingSessionStore;
	private final UserRepository userRepository;
	private final ContentRepository contentRepository;
	private final SimpUserRegistry simpUserRegistry;
	private final ApplicationEventPublisher eventPublisher;
	private final ScheduledExecutorService joinBroadcastScheduler = Executors.newScheduledThreadPool(2);

	@PreDestroy
	void shutdownScheduler() {
		joinBroadcastScheduler.shutdown();
	}

	public WatchingSessionService(
		WatchingSessionStore watchingSessionStore,
		UserRepository userRepository,
		ContentRepository contentRepository,
		@org.springframework.context.annotation.Lazy SimpUserRegistry simpUserRegistry,
		ApplicationEventPublisher eventPublisher
	) {
		this.watchingSessionStore = watchingSessionStore;
		this.userRepository = userRepository;
		this.contentRepository = contentRepository;
		this.simpUserRegistry = simpUserRegistry;
		this.eventPublisher = eventPublisher;
	}

	// 시청 세션 입장
	public Optional<WatchingSessionChange> join(UUID contentId, UUID userId, String sessionId) {
		log.info("[WATCHING_SESSION_JOIN] 시청 세션 입장 시작: contentId={}, userId={}, sessionId={}",
			contentId, userId, sessionId);

		User user = getUserOrThrow(userId);
		Content content = getContentOrThrow(contentId);

		Optional<Instant> joinedAt = watchingSessionStore.join(sessionId, userId, contentId);

		if (joinedAt.isEmpty()) {
			log.debug("[WATCHING_SESSION_JOIN] 추가 탭 입장 (브로드캐스트 없음): contentId={}, userId={}",
				contentId, userId);
			return Optional.empty();
		}

		WatchingSessionChange change = createChange(ChangeType.JOIN, user, content, userId, joinedAt.get());

		log.info("[WATCHING_SESSION_JOIN] 첫 탭 입장 완료: contentId={}, userId={}, watcherCount={}",
			contentId, userId, change.watcherCount());

		return Optional.of(change);
	}

	// 구독 등록 확인 후 JOIN 이벤트 publish (preSend 시점에는 구독 미등록 상태이므로 지연 publish)
	public void publishJoinAfterSubscriptionReady(
		String sessionId,
		String destination,
		UUID contentId,
		WatchingSessionChange change
	) {
		scheduleJoinBroadcast(sessionId, destination, contentId, change, 0);
	}

	private void scheduleJoinBroadcast(
		String sessionId,
		String destination,
		UUID contentId,
		WatchingSessionChange change,
		int attempt
	) {
		joinBroadcastScheduler.schedule(() -> {
			try {
				if (isSubscriptionReady(sessionId, destination)) {
					eventPublisher.publishEvent(new WatchingSessionEvent(contentId, change));
					log.debug("[WATCHING_SESSION_JOIN_BROADCAST] 구독 확인 후 JOIN publish: sessionId={}, attempt={}",
						sessionId, attempt + 1);
					return;
				}

				if (attempt + 1 >= JOIN_BROADCAST_MAX_RETRIES) {
					log.warn("[WATCHING_SESSION_JOIN_BROADCAST] 구독 확인 실패, fallback publish: sessionId={}, destination={}",
						sessionId, destination);
					eventPublisher.publishEvent(new WatchingSessionEvent(contentId, change));
					return;
				}

				scheduleJoinBroadcast(sessionId, destination, contentId, change, attempt + 1);
			} catch (Exception e) {
				log.error("[WATCHING_SESSION_JOIN_BROADCAST] JOIN 이벤트 publish 실패: sessionId={}", sessionId, e);
			}
		}, JOIN_BROADCAST_RETRY_INTERVAL_MS, TimeUnit.MILLISECONDS);
	}

	private boolean isSubscriptionReady(String sessionId, String destination) {
		return simpUserRegistry.getUsers().stream()
			.flatMap(user -> user.getSessions().stream())
			.filter(session -> session.getId().equals(sessionId))
			.flatMap(session -> session.getSubscriptions().stream())
			.anyMatch(sub -> destination.equals(sub.getDestination()));
	}

	// 시청 세션 퇴장
	public Optional<WatchingSessionChange> leave(UUID contentId, UUID userId, String sessionId) {
		log.info("[WATCHING_SESSION_LEAVE] 시청 세션 퇴장 시작: contentId={}, userId={}, sessionId={}",
			contentId, userId, sessionId);

		User user = getUserOrThrow(userId);
		Content content = getContentOrThrow(contentId);

		Optional<Instant> joinedAt = watchingSessionStore.leave(sessionId, userId, contentId);

		if (joinedAt.isEmpty()) {
			log.debug("[WATCHING_SESSION_LEAVE] 탭 닫힘 (아직 시청 중): contentId={}, userId={}", contentId, userId);
			return Optional.empty();
		}

		WatchingSessionChange change = createChange(ChangeType.LEAVE, user, content, userId, joinedAt.get());

		log.info("[WATCHING_SESSION_LEAVE] 마지막 탭 퇴장 완료: contentId={}, userId={}, watcherCount={}",
			contentId, userId, change.watcherCount());

		return Optional.of(change);
	}

	// DISCONNECT용: sessionId만으로 퇴장 처리
	public Optional<WatchingSessionChange> leaveBySessionId(String sessionId) {
		Optional<UUID> userId = watchingSessionStore.getUserId(sessionId);
		Optional<UUID> contentId = watchingSessionStore.getContentId(sessionId);

		if (userId.isEmpty() || contentId.isEmpty()) {
			watchingSessionStore.removeSession(sessionId);
			return Optional.empty();
		}

		return leave(contentId.get(), userId.get(), sessionId);
	}

	// 시청 중인지 확인
	public boolean isWatching(UUID contentId, UUID userId) {
		return watchingSessionStore.isViewing(contentId, userId);
	}

	// 시청자 수 조회
	public long getWatcherCount(UUID contentId) {
		return watchingSessionStore.getViewerCount(contentId);
	}

	// 특정 콘텐츠의 시청 세션 목록 조회 (커서 페이지네이션)
	public CursorResponse<WatchingSessionDto> findByContentId(UUID contentId, WatchingSessionPageRequest request) {
		log.debug("[WATCHING_SESSION_FIND_BY_CONTENT] 시청 세션 목록 조회 시작: contentId={}", contentId);

		List<WatchingSessionDto> sessions = getAllSessionsByContentId(contentId);

		if (request.watcherNameLike() != null && !request.watcherNameLike().isBlank()) {
			sessions = sessions.stream()
				.filter(s -> s.watcher().name().contains(request.watcherNameLike()))
				.toList();
		}

		long totalCount = sessions.size();

		boolean ascending = request.sortDirection() == SortDirection.ASCENDING;
		Comparator<WatchingSessionDto> comparator = Comparator
			.comparing(WatchingSessionDto::createdAt)
			.thenComparing(WatchingSessionDto::id);

		if (!ascending) {
			comparator = comparator.reversed();
		}

		sessions = sessions.stream().sorted(comparator).toList();

		if (request.cursor() != null && request.idAfter() != null) {
			Instant cursorCreatedAt = parseCursor(request.cursor());

			if (cursorCreatedAt != null) {
				UUID cursorId = request.idAfter();

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

	// 특정 사용자의 시청 세션 조회 (가장 최근 입장 세션 반환)
	public Optional<WatchingSessionDto> findByWatcherId(UUID watcherId) {
		log.debug("[WATCHING_SESSION_FIND_BY_WATCHER] 유저 시청 세션 조회 시작: watcherId={}", watcherId);

		Map<UUID, Instant> sessions = watchingSessionStore.getSessionsByUserId(watcherId);

		if (sessions.isEmpty()) {
			return Optional.empty();
		}

		Map.Entry<UUID, Instant> latest = sessions.entrySet().stream()
			.max(Comparator.comparing(Map.Entry::getValue))
			.orElseThrow();

		UUID contentId = latest.getKey();
		Instant joinedAt = latest.getValue();

		User user = getUserOrThrow(watcherId);
		Content content = getContentOrThrow(contentId);

		return Optional.of(new WatchingSessionDto(
			watcherId,
			joinedAt,
			new UserSummary(user.getId(), user.getName(), user.getProfileImageUrl()),
			toContentSummary(content)
		));
	}

	private List<WatchingSessionDto> getAllSessionsByContentId(UUID contentId) {
		Map<UUID, Instant> viewers = watchingSessionStore.getViewers(contentId);

		Content content = getContentOrThrow(contentId);
		ContentSummary contentSummary = toContentSummary(content);

		List<User> users = userRepository.findAllByIdInAndLockedFalse(viewers.keySet());

		return users.stream()
			.map(user -> {
				Instant joinedAt = viewers.get(user.getId());
				return new WatchingSessionDto(
					user.getId(),
					joinedAt,
					new UserSummary(user.getId(), user.getName(), user.getProfileImageUrl()),
					contentSummary
				);
			})
			.toList();
	}

	private WatchingSessionChange createChange(
		ChangeType type, User user, Content content, UUID userId, Instant joinedAt
	) {
		UserSummary watcher = new UserSummary(user.getId(), user.getName(), user.getProfileImageUrl());
		long watcherCount = watchingSessionStore.getViewerCount(content.getId());

		WatchingSessionDto sessionDto = new WatchingSessionDto(
			userId,
			joinedAt,
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

	private Instant parseCursor(String cursor) {
		try {
			return Instant.parse(cursor);
		} catch (DateTimeParseException e) {
			log.warn("[WATCHING_SESSION_CURSOR_PARSE_FAILED] 커서 파싱 실패: cursor={}", cursor);
			return null;
		}
	}

	private boolean isAfterCursor(WatchingSessionDto session, Instant cursorCreatedAt, UUID cursorId, boolean ascending) {
		int createdAtCompare = session.createdAt().compareTo(cursorCreatedAt);

		if (createdAtCompare == 0) {
			int idCompare = session.id().compareTo(cursorId);
			return ascending ? idCompare > 0 : idCompare < 0;
		}

		return ascending ? createdAtCompare > 0 : createdAtCompare < 0;
	}
}
