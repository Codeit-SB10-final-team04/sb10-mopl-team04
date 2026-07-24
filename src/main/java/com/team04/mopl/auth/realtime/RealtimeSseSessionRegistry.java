package com.team04.mopl.auth.realtime;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.team04.mopl.auth.exception.AuthException;
import com.team04.mopl.auth.session.AuthSessionStore;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class RealtimeSseSessionRegistry {

	private final AuthSessionStore authSessionStore;
	private final ConcurrentHashMap<UUID, List<AuthenticatedSession>> sessions = new ConcurrentHashMap<>();

	// SSE 연결과 발급 당시 인증 세션 바인딩
	public SseEmitter bind(UUID userId, UUID authSessionId, SseEmitter emitter) {
		// 인증 세션 식별자 없는 SSE 연결 차단
		if (authSessionId == null) {
			emitter.complete();
			return emitter;
		}

		// 사용자별 SSE 연결 및 인증 세션 등록
		AuthenticatedSession session = new AuthenticatedSession(userId, authSessionId, emitter);
		sessions.compute(userId, (id, userSessions) -> {
			List<AuthenticatedSession> target = userSessions;
			if (target == null) {
				target = new CopyOnWriteArrayList<>();
			}
			target.add(session);
			return target;
		});

		// SSE 생명주기 종료 시 추적 정보 제거
		emitter.onCompletion(() -> remove(userId, emitter));
		emitter.onTimeout(() -> remove(userId, emitter));
		emitter.onError(exception -> remove(userId, emitter));

		// JWT 검증과 SSE 바인딩 사이의 인증 세션 교체 방어
		try {
			if (authSessionStore.isActive(userId, authSessionId)) {
				return emitter;
			}
		} catch (AuthException exception) {
			log.warn("[SSE_AUTH_SESSION_VALIDATE_FAILED] SSE 인증 세션 검증 실패: userId={}",
				userId, exception);
		}

		close(session);

		return emitter;
	}

	public List<AuthenticatedSession> findByUserId(UUID userId) {
		return List.copyOf(sessions.getOrDefault(userId, List.of()));
	}

	// SSE 연결 종료 및 추적 정보 제거
	public boolean close(AuthenticatedSession session) {
		try {
			session.emitter().complete();
			return true;
		} catch (RuntimeException exception) {
			log.warn("[SSE_AUTH_SESSION_CLOSE_FAILED] 무효 SSE 연결 종료 실패: userId={}",
				session.userId(), exception);
			return false;
		} finally {
			remove(session.userId(), session.emitter());
		}
	}

	// 종료된 SSE 연결 제거 및 빈 사용자 항목 정리
	private void remove(UUID userId, SseEmitter emitter) {
		sessions.computeIfPresent(userId, (id, userSessions) -> {
			userSessions.removeIf(session -> session.emitter() == emitter);
			return userSessions.isEmpty() ? null : userSessions;
		});
	}

	public record AuthenticatedSession(
		UUID userId,
		UUID authSessionId,
		SseEmitter emitter
	) {
	}
}
