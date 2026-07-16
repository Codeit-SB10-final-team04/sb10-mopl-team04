package com.team04.mopl.auth.service;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team04.mopl.auth.dto.response.JwtDto;
import com.team04.mopl.auth.exception.AuthErrorCode;
import com.team04.mopl.auth.exception.AuthException;
import com.team04.mopl.auth.security.MoplUserDetails;
import com.team04.mopl.auth.security.jwt.JwtTokenProvider;
import com.team04.mopl.auth.security.jwt.RefreshTokenGenerator;
import com.team04.mopl.auth.security.jwt.TokenHasher;
import com.team04.mopl.auth.service.dto.TokenRefreshResult;
import com.team04.mopl.auth.session.AuthSessionData;
import com.team04.mopl.auth.session.AuthSessionStore;
import com.team04.mopl.user.entity.User;
import com.team04.mopl.user.mapper.UserMapper;
import com.team04.mopl.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthTokenService {

	// access token 생성과 만료 시각 계산을 담당한다.
	private final JwtTokenProvider jwtTokenProvider;

	// 브라우저 쿠키로 전달할 새로운 refresh token 원문을 생성한다.
	private final RefreshTokenGenerator refreshTokenGenerator;

	// refresh token 원문이 Redis에 저장되지 않도록 단방향 해시를 생성한다.
	private final TokenHasher tokenHasher;

	// refresh token 조회와 회전을 Redis 저장 방식과 분리된 계약으로 수행한다.
	private final AuthSessionStore authSessionStore;

	// Redis 세션에는 User 엔티티를 넣지 않으므로 갱신 시 최신 계정 상태를 DB에서 다시 조회한다.
	private final UserRepository userRepository;

	// 최신 User 엔티티를 API 응답용 UserDto로 변환한다.
	private final UserMapper userMapper;

	// refresh token을 검증하고 access token과 refresh token을 모두 새로 발급한다.
	public TokenRefreshResult refresh(String refreshToken) {
		// 쿠키에 refresh token이 없으면 Redis 조회를 시도하지 않고 즉시 인증 실패로 처리한다.
		if (refreshToken == null || refreshToken.isBlank()) {
			log.warn("[AUTH_REFRESH_TOKEN] 토큰 재발급 실패: refresh token 쿠키 없음");
			throw new AuthException(AuthErrorCode.AUTH_MISSING_REFRESH_TOKEN);
		}

		// 이번 갱신 과정에서 만료 계산과 저장 시각이 일치하도록 기준 시각을 한 번만 생성한다.
		Instant refreshedAt = Instant.now();

		// 원문 token은 로그나 저장소에 남기지 않고 해시값만 이후 조회에 사용한다.
		String currentRefreshTokenHash = tokenHasher.hash(refreshToken);

		// refresh hash 역인덱스로 Redis 인증 세션을 조회한다.
		AuthSessionData authSession = authSessionStore.findByRefreshTokenHash(currentRefreshTokenHash)
			.orElseThrow(() -> {
				log.warn("[AUTH_REFRESH_TOKEN] 토큰 재발급 실패: refresh token에 해당하는 인증 세션 없음");
				return new AuthException(AuthErrorCode.AUTH_INVALID_REFRESH_TOKEN);
			});

		// 저장된 만료 시각을 먼저 확인하고 만료된 세션은 Redis에서 제거한다.
		validateRefreshTokenNotExpired(authSession, refreshedAt);

		// Redis에는 userId만 있으므로 DB에서 최신 사용자 권한과 잠금 상태를 가져온다.
		User user = findUser(authSession);

		// 잠긴 계정이면 기존 세션을 모두 무효화하고 token 재발급을 막는다.
		validateUserNotLocked(authSession, user);

		// 최신 User 정보로 JWT claim과 Spring Security 권한을 구성한다.
		MoplUserDetails userDetails = MoplUserDetails.from(user);

		// 한 기준 시각에서 새 access token과 refresh token의 만료 시각을 계산한다.
		Instant accessExpiresAt = jwtTokenProvider.calculateAccessExpiresAt(refreshedAt);
		Instant refreshExpiresAt = jwtTokenProvider.calculateRefreshExpiresAt(refreshedAt);

		// 기존 sessionId는 유지해 같은 로그인 세션 안에서 access token만 재발급한다.
		String newAccessToken = jwtTokenProvider.generateAccessToken(
			userDetails,
			authSession.sessionId(),
			refreshedAt,
			accessExpiresAt
		);

		// refresh token은 재사용 공격을 막기 위해 매 갱신마다 새로운 원문을 생성한다.
		String newRefreshToken = refreshTokenGenerator.generate();

		// Redis에는 새 refresh token도 원문이 아닌 해시만 저장한다.
		String newRefreshTokenHash = tokenHasher.hash(newRefreshToken);

		// 현재 hash가 그대로일 때만 새 hash와 만료 시각으로 원자 갱신한다.
		boolean refreshed = authSessionStore.refresh(
			user.getId(),
			authSession.sessionId(),
			currentRefreshTokenHash,
			newRefreshTokenHash,
			accessExpiresAt,
			refreshExpiresAt,
			refreshedAt
		);

		// 다른 요청이 먼저 같은 refresh token을 사용했다면 현재 요청은 재사용으로 보고 실패시킨다.
		if (!refreshed) {
			log.warn("[AUTH_REFRESH_TOKEN] 토큰 재발급 실패: 인증 세션 갱신 실패, userId={}, sessionId={}",
				user.getId(),
				authSession.sessionId()
			);
			throw new AuthException(AuthErrorCode.AUTH_INVALID_REFRESH_TOKEN);
		}

		// Redis 세션 회전까지 완료된 뒤에만 성공 로그와 응답 생성을 진행한다.
		log.info("[AUTH_REFRESH_TOKEN] 토큰 재발급 성공: userId={}, sessionId={}",
			user.getId(),
			authSession.sessionId()
		);

		// DB에서 읽은 최신 사용자 상태와 새 access token을 응답 DTO로 묶는다.
		JwtDto jwtDto = new JwtDto(
			userMapper.toDto(user),
			newAccessToken
		);

		// 새 refresh token 원문은 Controller가 HttpOnly 쿠키에 기록할 수 있도록 별도로 반환한다.
		return new TokenRefreshResult(
			jwtDto,
			newRefreshToken
		);
	}

	// refresh token 만료 여부를 검증하고 만료된 세션은 Redis에서 삭제한다.
	private void validateRefreshTokenNotExpired(AuthSessionData authSession, Instant refreshedAt) {
		// 아직 만료되지 않았다면 다음 사용자 검증 단계로 진행한다.
		if (!authSession.isRefreshTokenExpired(refreshedAt)) {
			return;
		}

		// 만료된 세션과 refresh 역인덱스를 함께 삭제해 이후 재시도를 빠르게 차단한다.
		authSessionStore.delete(
			authSession.userId(),
			authSession.sessionId()
		);

		// 운영 로그에는 token 값 없이 세션 식별 정보만 남긴다.
		log.warn("[AUTH_REFRESH_TOKEN] 토큰 재발급 실패: 만료된 refresh token, userId={}, sessionId={}",
			authSession.userId(),
			authSession.sessionId()
		);

		// 클라이언트가 만료와 단순 불일치를 구분할 수 있도록 전용 오류를 반환한다.
		throw new AuthException(AuthErrorCode.AUTH_EXPIRED_REFRESH_TOKEN);
	}

	// Redis 세션의 userId로 최신 사용자 엔티티를 조회한다.
	private User findUser(AuthSessionData authSession) {
		// 세션 생성 후 사용자가 사라졌다면 해당 세션도 더 이상 유효하지 않다.
		return userRepository.findById(authSession.userId())
			.orElseThrow(() -> {
				// 고아 세션과 refresh 역인덱스를 제거해 같은 token의 반복 조회를 막는다.
				authSessionStore.delete(authSession.userId(), authSession.sessionId());

				// 존재하지 않는 사용자와 연결된 세션 정보를 token 값 없이 기록한다.
				log.warn("[AUTH_REFRESH_TOKEN] 토큰 재발급 실패: 사용자가 존재하지 않음, userId={}, sessionId={}",
					authSession.userId(),
					authSession.sessionId()
				);

				// 외부에는 사용자 존재 여부를 자세히 노출하지 않고 유효하지 않은 refresh token으로 응답한다.
				return new AuthException(AuthErrorCode.AUTH_INVALID_REFRESH_TOKEN);
			});
	}

	// 잠긴 계정인지 검증하고 잠긴 계정이면 사용자의 전체 인증 세션을 삭제한다.
	private void validateUserNotLocked(AuthSessionData authSession, User user) {
		// 잠기지 않은 사용자는 새 token 발급을 계속 진행한다.
		if (!user.isLocked()) {
			return;
		}

		// 잠금은 사용자 단위 정책이므로 현재 sessionId와 관계없이 활성 세션 전체를 제거한다.
		authSessionStore.deleteByUserId(user.getId());

		// 잠금으로 인한 재발급 차단 사실을 token 값 없이 기록한다.
		log.warn("[AUTH_REFRESH_TOKEN] 토큰 재발급 실패: 잠긴 계정, userId={}, sessionId={}",
			user.getId(),
			authSession.sessionId()
		);

		// 로그인과 동일한 잠금 계정 오류로 응답해 인증 정책을 일관되게 유지한다.
		throw new AuthException(AuthErrorCode.AUTH_LOCKED_ACCOUNT);
	}
}
