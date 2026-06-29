package com.team04.mopl.auth.service;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team04.mopl.auth.dto.response.JwtDto;
import com.team04.mopl.auth.service.dto.TokenRefreshResult;
import com.team04.mopl.auth.entity.AuthSession;
import com.team04.mopl.auth.exception.AuthErrorCode;
import com.team04.mopl.auth.exception.AuthException;
import com.team04.mopl.auth.security.MoplUserDetails;
import com.team04.mopl.auth.security.jwt.JwtTokenProvider;
import com.team04.mopl.auth.security.jwt.RefreshTokenGenerator;
import com.team04.mopl.auth.security.jwt.TokenHasher;
import com.team04.mopl.auth.session.AuthSessionStore;
import com.team04.mopl.user.entity.User;
import com.team04.mopl.user.mapper.UserMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthTokenService {

	private final JwtTokenProvider jwtTokenProvider;
	private final RefreshTokenGenerator refreshTokenGenerator;
	private final TokenHasher tokenHasher;
	private final AuthSessionStore authSessionStore;
	private final UserMapper userMapper;

	// refresh token으로 access token과 refresh token을 재발급
	@Transactional
	public TokenRefreshResult refresh(String refreshToken) {
		if (refreshToken == null || refreshToken.isBlank()) {
			log.warn("[AUTH_REFRESH_TOKEN] 토큰 재발급 실패: refresh token 쿠키 없음");
			throw new AuthException(AuthErrorCode.AUTH_MISSING_REFRESH_TOKEN);
		}

		Instant refreshedAt = Instant.now();
		String currentRefreshTokenHash = tokenHasher.hash(refreshToken);

		AuthSession authSession = authSessionStore.findByRefreshTokenHash(currentRefreshTokenHash)
			.orElseThrow(() -> {
				log.warn("[AUTH_REFRESH_TOKEN] 토큰 재발급 실패: refresh token에 해당하는 인증 세션 없음");
				return new AuthException(AuthErrorCode.AUTH_INVALID_REFRESH_TOKEN);
			});

		validateRefreshTokenNotExpired(authSession, refreshedAt);
		validateUserNotLocked(authSession);

		User user = authSession.getUser();
		MoplUserDetails userDetails = MoplUserDetails.from(user);

		Instant accessExpiresAt = jwtTokenProvider.calculateAccessExpiresAt(refreshedAt);
		Instant refreshExpiresAt = jwtTokenProvider.calculateRefreshExpiresAt(refreshedAt);

		String newAccessToken = jwtTokenProvider.generateAccessToken(
			userDetails,
			authSession.getSessionId(),
			refreshedAt,
			accessExpiresAt
		);

		String newRefreshToken = refreshTokenGenerator.generate();
		String newRefreshTokenHash = tokenHasher.hash(newRefreshToken);

		authSessionStore.refresh(
			user.getId(),
			currentRefreshTokenHash,
			newRefreshTokenHash,
			accessExpiresAt,
			refreshExpiresAt,
			refreshedAt
		).orElseThrow(() -> {
			log.warn("[AUTH_REFRESH_TOKEN] 토큰 재발급 실패: 인증 세션 갱신 실패, userId={}, sessionId={}", user.getId(), authSession.getSessionId());
			return new AuthException(AuthErrorCode.AUTH_INVALID_REFRESH_TOKEN);
		});

		log.info("[AUTH_REFRESH_TOKEN] 토큰 재발급 성공: userId={}, sessionId={}", user.getId(), authSession.getSessionId());

		JwtDto jwtDto = new JwtDto(
			userMapper.toDto(user),
			newAccessToken
		);

		return new TokenRefreshResult(
			jwtDto,
			newRefreshToken
		);
	}

	// refresh token 만료 여부를 검증하고 만료된 세션은 삭제
	private void validateRefreshTokenNotExpired(AuthSession authSession, Instant refreshedAt) {
		User user = authSession.getUser();

		if (!authSession.isRefreshTokenExpired(refreshedAt)) {
			return;
		}

		authSessionStore.delete(
			user.getId(),
			authSession.getSessionId()
		);

		log.warn("[AUTH_REFRESH_TOKEN] 토큰 재발급 실패: 만료된 refresh token, userId={}, sessionId={}", user.getId(), authSession.getSessionId());

		throw new AuthException(AuthErrorCode.AUTH_EXPIRED_REFRESH_TOKEN);
	}

	// 잠긴 계정인지 검증하고 잠긴 계정이면 사용자 인증 세션 삭제
	private void validateUserNotLocked(AuthSession authSession) {
		User user = authSession.getUser();

		if (!user.isLocked()) {
			return;
		}

		authSessionStore.deleteByUserId(user.getId());

		log.warn("[AUTH_REFRESH_TOKEN] 토큰 재발급 실패: 잠긴 계정, userId={}, sessionId={}", user.getId(), authSession.getSessionId());

		throw new AuthException(AuthErrorCode.AUTH_LOCKED_ACCOUNT);
	}
}
