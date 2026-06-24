package com.team04.mopl.auth.session;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.team04.mopl.auth.entity.AuthSession;
import com.team04.mopl.auth.repository.AuthSessionRepository;
import com.team04.mopl.user.entity.User;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DbAuthSessionStore implements AuthSessionStore {
	private final AuthSessionRepository authSessionRepository;
	private final EntityManager entityManager;

	// 로그인 성공 시 기존 세션을 제거하고 새 세션을 저장
	@Override
	@Transactional
	public AuthSession replace(
		UUID userId,
		UUID sessionId,
		String refreshTokenHash,
		Instant accessExpiresAt,
		Instant refreshExpiresAt,
		Instant issuedAt
	) {
		Objects.requireNonNull(userId, "userId는 필수입니다.");

		// 같은 사용자에 대한 동시 replace()를 직렬화하기 위해 사용자 row에 비관적 락
		// 동시에 로그인 요청이 들어오면 delete -> insert 과정에서 user_id unique 충돌이 날 수 있음
		User user = findUserForUpdate(userId);

		// 사용자당 활성 세션은 하나만 있어야 하므로 기존 세션이 있으면 삭제
		deleteByUser(user);

		// 기존 세션 삭제를 DB에 먼저 반영하여 user_id unique 충돌 방지
		authSessionRepository.flush();

		AuthSession authSession = AuthSession.builder()
			.sessionId(sessionId)
			.user(user)
			.refreshTokenHash(refreshTokenHash)
			.accessExpiresAt(accessExpiresAt)
			.refreshExpiresAt(refreshExpiresAt)
			.updatedAt(issuedAt)
			.build();

		// 새 인증 세션 저장
		return authSessionRepository.save(authSession);
	}

	// 사용자 활성 인증 세션 조회
	@Override
	public Optional<AuthSession> findByUserId(UUID userId) {
		// DB 구현체 내부에서만 userId를 User 프록시 객체로 변환하여 활용
		User user = getUserReference(userId);

		return authSessionRepository.findByUser(user);
	}

	// 사용자(userId + sessionId 조합)가 현재 활성 세션인지 확인
	@Override
	public boolean isActive(UUID userId, UUID sessionId) {

		if (userId == null || sessionId == null) {
			return false;
		}

		// userId를 User 프록시 객체로 변환
		User user = getUserReference(userId);

		// DB에 userId + sessionId 조합이 존재 -> 현재 살아있는 세션
		// 로그아웃, 권한 변경, 계정 잠금으로 세션이 삭제되면 false가 됨
		return authSessionRepository.existsByUserAndSessionId(user, sessionId);
	}

	// refresh token 재발급 시 인증 세션의 refresh token과 만료시간을 갱신
	@Override
	@Transactional
	public Optional<AuthSession> refresh(
		UUID userId,
		String refreshTokenHash,
		Instant accessExpiresAt,
		Instant refreshExpiresAt,
		Instant refreshedAt
	) {
		// refresh token 재발급 시 기존 인증 세션 조회
		User user = getUserReference(userId);

		return authSessionRepository.findByUser(user)
			.map(authSession -> {
				authSession.refresh(
					refreshTokenHash,
					accessExpiresAt,
					refreshExpiresAt,
					refreshedAt
				);

				return authSession;
			});
	}

	// 사용자 인증 세션 삭제
	@Override
	@Transactional
	public void deleteByUserId(UUID userId) {
		User user = getUserReference(userId);

		deleteByUser(user);
	}

	private void deleteByUser(User user) {
		Objects.requireNonNull(user, "user는 필수입니다.");

		// 세션이 없어도 예외를 던지지 않음
		// 로그아웃/강제 로그아웃 흐름에서 멱등하게 호출 가능해야 함
		authSessionRepository.findByUser(user)
			.ifPresent(authSessionRepository::delete);
	}

	private User getUserReference(UUID userId) {
		Objects.requireNonNull(userId, "userId는 필수입니다.");

		// DB 조회를 하지 않고 id만 가진 User 프록시를 만들어서 활용
		return entityManager.getReference(User.class, userId);
	}

	private User findUserForUpdate(UUID userId) {
		Objects.requireNonNull(userId, "userId는 필수입니다.");

		// replace() 동시 호출 방지를 위해 PESSIMISTIC_WRITE 락 적용
		// 같은 사용자에 대한 로그인 요청이 동시에 들어오면 user row 기준으로 직렬화됨
		User user = entityManager.find(
			User.class,
			userId,
			LockModeType.PESSIMISTIC_WRITE
		);

		if (user == null) {
			throw new IllegalArgumentException("사용자를 찾을 수 없습니다.");
		}

		return user;
	}
}
