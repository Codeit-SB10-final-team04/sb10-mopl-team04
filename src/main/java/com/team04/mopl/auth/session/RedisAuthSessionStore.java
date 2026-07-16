package com.team04.mopl.auth.session;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import com.team04.mopl.auth.exception.AuthErrorCode;
import com.team04.mopl.auth.exception.AuthException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisAuthSessionStore implements AuthSessionStore {

	// Redis Cluster 동일 hash slot 배치
	private static final String SESSION_KEY_PREFIX = "mopl:{auth-session}:session:";
	private static final String REFRESH_KEY_PREFIX = "mopl:{auth-session}:refresh:";

	// refresh 역인덱스 미존재 시 더미 키
	private static final String MISSING_REFRESH_KEY_SUFFIX = ":missing-refresh";

	// CAS 최대 재시도 횟수
	private static final int MAX_COMPARE_AND_SET_RETRIES = 5;

	private static final String USER_ID_FIELD = "userId";
	private static final String SESSION_ID_FIELD = "sessionId";
	private static final String REFRESH_TOKEN_HASH_FIELD = "refreshTokenHash";
	private static final String ACCESS_EXPIRES_AT_FIELD = "accessExpiresAt";
	private static final String REFRESH_EXPIRES_AT_FIELD = "refreshExpiresAt";
	private static final String LAST_REFRESHED_AT_FIELD = "lastRefreshedAt";
	private static final String UPDATED_AT_FIELD = "updatedAt";

	// Lua 공통 반환 코드
	private static final long SUCCESS = 1L;
	private static final long RETRY = -1L;

	// 로그인 세션 원자 교체 스크립트
	private static final DefaultRedisScript<Long> REPLACE_SCRIPT = loadScript(
		"redis/auth-session/replace.lua"
	);

	// refresh token 원자 회전 스크립트
	private static final DefaultRedisScript<Long> REFRESH_SCRIPT = loadScript(
		"redis/auth-session/refresh.lua"
	);

	// 사용자 세션 강제 삭제 스크립트
	private static final DefaultRedisScript<Long> DELETE_BY_USER_SCRIPT = loadScript(
		"redis/auth-session/delete-by-user.lua"
	);

	// sessionId 기반 로그아웃 스크립트
	private static final DefaultRedisScript<Long> DELETE_SCRIPT = loadScript(
		"redis/auth-session/delete.lua"
	);

	// 문자열 기반 Redis 연산 템플릿
	private final StringRedisTemplate redisTemplate;

	// 로그인 세션 교체
	@Override
	public void replace(
		UUID userId,
		UUID sessionId,
		String refreshTokenHash,
		Instant accessExpiresAt,
		Instant refreshExpiresAt,
		Instant issuedAt
	) {
		// 세션 값 검증
		AuthSessionData newSession = new AuthSessionData(
			Objects.requireNonNull(userId, "userId는 필수입니다."),
			Objects.requireNonNull(sessionId, "sessionId는 필수입니다."),
			requireText(refreshTokenHash, "Refresh Token 해시는 필수입니다."),
			Objects.requireNonNull(accessExpiresAt, "accessExpiresAt은 필수입니다."),
			Objects.requireNonNull(refreshExpiresAt, "refreshExpiresAt은 필수입니다."),
			null,
			Objects.requireNonNull(issuedAt, "issuedAt은 필수입니다.")
		);

		// Redis 키 생성
		String sessionKey = sessionKey(newSession.userId());
		String newRefreshKey = refreshKey(newSession.refreshTokenHash());

		// CAS 충돌 재시도
		for (int attempt = 0; attempt < MAX_COMPARE_AND_SET_RETRIES; attempt++) {
			String currentRefreshTokenHash = readCurrentRefreshTokenHash(sessionKey);
			String oldRefreshKey = oldRefreshKey(sessionKey, currentRefreshTokenHash);

			// 신규 세션 및 역인덱스 원자 저장
			long result = executeScript(
				REPLACE_SCRIPT,
				List.of(sessionKey, oldRefreshKey, newRefreshKey),
				nullToEmpty(currentRefreshTokenHash),
				newSession.userId().toString(),
				newSession.sessionId().toString(),
				newSession.refreshTokenHash(),
				toEpochMilli(newSession.accessExpiresAt()),
				toEpochMilli(newSession.refreshExpiresAt()),
				toEpochMilli(newSession.updatedAt())
			);

			// 세션 교체 완료
			if (result == SUCCESS) {
				return;
			}

			// 비정상 스크립트 결과
			if (result != RETRY) {
				throw serviceError("인증 세션 교체 결과가 올바르지 않습니다.");
			}
		}

		// CAS 재시도 초과
		throw serviceError("동시 요청으로 인증 세션 교체에 실패했습니다.");
	}

	// 사용자 세션 조회
	@Override
	public Optional<AuthSessionData> findByUserId(UUID userId) {
		// 사용자 ID 검증
		Objects.requireNonNull(userId, "userId는 필수입니다.");

		// 세션 Hash 조회
		Map<String, String> values = executeRedis(() -> hashOperations().entries(sessionKey(userId)));

		// 세션 미존재
		if (values.isEmpty()) {
			return Optional.empty();
		}

		// 세션 데이터 변환
		return Optional.of(toAuthSessionData(values));
	}

	// 활성 세션 검증
	@Override
	public boolean isActive(UUID userId, UUID sessionId) {
		// JWT 세션 식별자 검증
		UUID requiredUserId = Objects.requireNonNull(userId, "userId는 필수입니다.");
		UUID requiredSessionId = Objects.requireNonNull(sessionId, "sessionId는 필수입니다.");

		List<String> activeSession = executeRedis(
			() -> hashOperations().multiGet(
				sessionKey(requiredUserId),
				List.of(SESSION_ID_FIELD, REFRESH_TOKEN_HASH_FIELD)
			)
		);

		// sessionId 및 refresh hash 검증
		return activeSession.size() == 2
			&& requiredSessionId.toString().equals(activeSession.get(0))
			&& activeSession.get(1) != null
			&& !activeSession.get(1).isBlank();
	}

	// refresh hash 기반 세션 조회
	@Override
	public Optional<AuthSessionData> findByRefreshTokenHash(String refreshTokenHash) {
		// refresh hash 검증
		String requiredRefreshTokenHash = requireText(
			refreshTokenHash,
			"Refresh Token 해시는 필수입니다."
		);

		// refresh 역인덱스 조회
		String refreshKey = refreshKey(requiredRefreshTokenHash);
		String userIdValue = executeRedis(() -> redisTemplate.opsForValue().get(refreshKey));

		// 역인덱스 미존재
		if (userIdValue == null) {
			return Optional.empty();
		}

		// userId 형식 검증
		UUID userId;
		try {
			userId = UUID.fromString(userIdValue);
		} catch (IllegalArgumentException exception) {
			throw new AuthException(AuthErrorCode.AUTH_SERVICE_ERROR, exception);
		}

		// 역인덱스와 세션 교차 검증
		Optional<AuthSessionData> authSession = findByUserId(userId)
			.filter(session -> session.matchesRefreshTokenHash(requiredRefreshTokenHash));

		// 불일치 역인덱스 정리
		if (authSession.isEmpty()) {
			executeRedis(() -> redisTemplate.delete(refreshKey));
		}

		return authSession;
	}

	// refresh token 원자 회전
	@Override
	public boolean refresh(
		UUID userId,
		UUID sessionId,
		String currentRefreshTokenHash,
		String newRefreshTokenHash,
		Instant accessExpiresAt,
		Instant refreshExpiresAt,
		Instant refreshedAt
	) {
		// 현재 refresh hash 검증
		String currentHash = requireText(
			currentRefreshTokenHash,
			"현재 Refresh Token 해시는 필수입니다."
		);

		// 신규 refresh hash 검증
		String newHash = requireText(
			newRefreshTokenHash,
			"새 Refresh Token 해시는 필수입니다."
		);
		Instant requiredRefreshedAt = Objects.requireNonNull(refreshedAt, "refreshedAt은 필수입니다.");

		// 갱신 세션 값 검증
		AuthSessionData refreshedSession = new AuthSessionData(
			Objects.requireNonNull(userId, "userId는 필수입니다."),
			Objects.requireNonNull(sessionId, "sessionId는 필수입니다."),
			newHash,
			Objects.requireNonNull(accessExpiresAt, "accessExpiresAt은 필수입니다."),
			Objects.requireNonNull(refreshExpiresAt, "refreshExpiresAt은 필수입니다."),
			requiredRefreshedAt,
			requiredRefreshedAt
		);

		// refresh hash 및 세션 원자 교체
		long result = executeScript(
			REFRESH_SCRIPT,
			List.of(sessionKey(refreshedSession.userId()), refreshKey(currentHash), refreshKey(newHash)),
			refreshedSession.userId().toString(),
			refreshedSession.sessionId().toString(),
			currentHash,
			newHash,
			toEpochMilli(refreshedSession.accessExpiresAt()),
			toEpochMilli(refreshedSession.refreshExpiresAt()),
			toEpochMilli(refreshedSession.updatedAt())
		);

		// 원자 교체 결과
		return result == SUCCESS;
	}

	// 사용자 세션 삭제
	@Override
	public void deleteByUserId(UUID userId) {
		// 사용자 ID 검증
		Objects.requireNonNull(userId, "userId는 필수입니다.");

		// 세션 키 생성
		String sessionKey = sessionKey(userId);

		// CAS 충돌 재시도
		for (int attempt = 0; attempt < MAX_COMPARE_AND_SET_RETRIES; attempt++) {
			// 현재 refresh hash 조회
			String currentRefreshTokenHash = readCurrentRefreshTokenHash(sessionKey);

			// 세션 및 역인덱스 원자 삭제
			long result = executeScript(
				DELETE_BY_USER_SCRIPT,
				List.of(sessionKey, oldRefreshKey(sessionKey, currentRefreshTokenHash)),
				nullToEmpty(currentRefreshTokenHash)
			);

			// 멱등 삭제 완료
			if (result != RETRY) {
				return;
			}
		}

		// CAS 재시도 초과
		throw serviceError("동시 요청으로 사용자 인증 세션 삭제에 실패했습니다.");
	}

	// sessionId 기반 세션 삭제
	@Override
	public void delete(UUID userId, UUID sessionId) {
		// 세션 식별자 검증
		Objects.requireNonNull(userId, "userId는 필수입니다.");
		Objects.requireNonNull(sessionId, "sessionId는 필수입니다.");

		// 세션 키 생성
		String sessionKey = sessionKey(userId);

		// CAS 충돌 재시도
		for (int attempt = 0; attempt < MAX_COMPARE_AND_SET_RETRIES; attempt++) {
			// 현재 refresh hash 조회
			String currentRefreshTokenHash = readCurrentRefreshTokenHash(sessionKey);

			// 현재 세션 원자 삭제
			long result = executeScript(
				DELETE_SCRIPT,
				List.of(sessionKey, oldRefreshKey(sessionKey, currentRefreshTokenHash)),
				nullToEmpty(currentRefreshTokenHash),
				sessionId.toString()
			);

			// 멱등 삭제 완료
			if (result != RETRY) {
				return;
			}
		}

		// CAS 재시도 초과
		throw serviceError("동시 요청으로 인증 세션 삭제에 실패했습니다.");
	}

	// 현재 refresh hash 조회
	private String readCurrentRefreshTokenHash(String sessionKey) {
		return executeRedis(() -> hashOperations().get(sessionKey, REFRESH_TOKEN_HASH_FIELD));
	}

	// Redis Hash 세션 변환
	private AuthSessionData toAuthSessionData(Map<String, String> values) {
		try {
			// 마지막 갱신 시각 조회
			String lastRefreshedAt = values.get(LAST_REFRESHED_AT_FIELD);

			// Redis 필드의 도메인 타입 변환
			return new AuthSessionData(
				UUID.fromString(requireField(values, USER_ID_FIELD)),
				UUID.fromString(requireField(values, SESSION_ID_FIELD)),
				requireField(values, REFRESH_TOKEN_HASH_FIELD),
				toInstant(requireField(values, ACCESS_EXPIRES_AT_FIELD)),
				toInstant(requireField(values, REFRESH_EXPIRES_AT_FIELD)),
				lastRefreshedAt == null || lastRefreshedAt.isBlank() ? null : toInstant(lastRefreshedAt),
				toInstant(requireField(values, UPDATED_AT_FIELD))
			);
		} catch (AuthException exception) {
			// 인증 도메인 오류 유지
			throw exception;
		} catch (RuntimeException exception) {
			// 손상된 Redis 데이터 오류 변환
			throw new AuthException(AuthErrorCode.AUTH_SERVICE_ERROR, exception);
		}
	}

	// Lua 스크립트 실행
	private long executeScript(
		DefaultRedisScript<Long> script,
		List<String> keys,
		String... arguments
	) {
		// 스크립트 실행 결과
		Long result = executeRedis(() -> redisTemplate.execute(script, keys, (Object[])arguments));

		// 결과 누락 검증
		if (result == null) {
			throw serviceError("Redis 인증 세션 스크립트 실행 결과가 없습니다.");
		}

		return result;
	}

	// Redis 예외 변환
	private <T> T executeRedis(Supplier<T> operation) {
		try {
			return operation.get();
		} catch (DataAccessException exception) {
			log.error("Redis 인증 세션 처리 실패", exception);
			throw new AuthException(AuthErrorCode.AUTH_SERVICE_ERROR, exception);
		}
	}

	// Redis Hash 연산 객체
	private HashOperations<String, String, String> hashOperations() {
		return redisTemplate.opsForHash();
	}

	// Lua 스크립트 로드
	private static DefaultRedisScript<Long> loadScript(String path) {
		DefaultRedisScript<Long> script = new DefaultRedisScript<>();
		script.setLocation(new ClassPathResource(path));
		script.setResultType(Long.class);

		return script;
	}

	// 사용자 세션 키 생성
	private static String sessionKey(UUID userId) {
		return SESSION_KEY_PREFIX + userId;
	}

	// refresh 역인덱스 키 생성
	private static String refreshKey(String refreshTokenHash) {
		return REFRESH_KEY_PREFIX + refreshTokenHash;
	}

	// 기존 refresh 역인덱스 키 생성
	private static String oldRefreshKey(String sessionKey, String currentRefreshTokenHash) {
		// refresh 역인덱스 미존재 키
		if (currentRefreshTokenHash == null) {
			return sessionKey + MISSING_REFRESH_KEY_SUFFIX;
		}

		return refreshKey(currentRefreshTokenHash);
	}

	// Redis Hash 필수 필드 검증
	private static String requireField(Map<String, String> values, String field) {
		String value = values.get(field);

		if (value == null || value.isBlank()) {
			throw serviceError("Redis 인증 세션 필드가 누락되었습니다: " + field);
		}

		return value;
	}

	// 필수 문자열 검증
	private static String requireText(String value, String message) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(message);
		}

		return value;
	}

	// Lua null 비교용 빈 문자열 변환
	private static String nullToEmpty(String value) {
		return value == null ? "" : value;
	}

	// Instant의 epoch millisecond 변환
	private static String toEpochMilli(Instant instant) {
		return Long.toString(instant.toEpochMilli());
	}

	// epoch millisecond의 Instant 변환
	private static Instant toInstant(String epochMilli) {
		return Instant.ofEpochMilli(Long.parseLong(epochMilli));
	}

	// 인증 서비스 오류 생성
	private static AuthException serviceError(String message) {
		return new AuthException(
			AuthErrorCode.AUTH_SERVICE_ERROR,
			new IllegalStateException(message)
		);
	}
}
