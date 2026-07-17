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

	private final JwtTokenProvider jwtTokenProvider;
	private final RefreshTokenGenerator refreshTokenGenerator;
	private final TokenHasher tokenHasher;
	private final AuthSessionStore authSessionStore;
	private final UserRepository userRepository;
	private final UserMapper userMapper;

	// refresh token으로 access token과 refresh token을 재발급
	public TokenRefreshResult refresh(String refreshToken) {
		// refresh token 필수값 검증
		if (refreshToken == null || refreshToken.isBlank()) {
			log.warn("[AUTH_REFRESH_TOKEN] 토큰 재발급 실패: refresh token 쿠키 없음");
			throw new AuthException(AuthErrorCode.AUTH_MISSING_REFRESH_TOKEN);
		}

		// 갱신 기준 시각
		Instant refreshedAt = Instant.now();

		// 현재 refresh token 해시
		String currentRefreshTokenHash = tokenHasher.hash(refreshToken);

		// 인증 세션 조회
		AuthSessionData authSession = authSessionStore.findByRefreshTokenHash(currentRefreshTokenHash)
			.orElseThrow(() -> {
				log.warn("[AUTH_REFRESH_TOKEN] 토큰 재발급 실패: refresh token에 해당하는 인증 세션 없음");
				return new AuthException(AuthErrorCode.AUTH_INVALID_REFRESH_TOKEN);
			});

		// refresh token 만료 검증
		validateRefreshTokenNotExpired(authSession, refreshedAt);

		// 최신 사용자 조회
		User user = findUser(authSession);

		// 계정 잠금 검증
		validateUserNotLocked(authSession, user);

		// 인증 사용자 정보 구성
		MoplUserDetails userDetails = MoplUserDetails.from(user);

		// 새 token 만료 시각 계산
		Instant accessExpiresAt = jwtTokenProvider.calculateAccessExpiresAt(refreshedAt);
		Instant refreshExpiresAt = jwtTokenProvider.calculateRefreshExpiresAt(refreshedAt);

		// access token 재발급
		String newAccessToken = jwtTokenProvider.generateAccessToken(
			userDetails,
			authSession.sessionId(),
			refreshedAt,
			accessExpiresAt
		);

		// refresh token 재발급
		String newRefreshToken = refreshTokenGenerator.generate();

		// 새 refresh token 해시
		String newRefreshTokenHash = tokenHasher.hash(newRefreshToken);

		// 인증 세션 원자 갱신
		boolean refreshed = authSessionStore.refresh(
			user.getId(),
			authSession.sessionId(),
			currentRefreshTokenHash,
			newRefreshTokenHash,
			accessExpiresAt,
			refreshExpiresAt,
			refreshedAt
		);

		// refresh token 회전 결과 검증
		if (!refreshed) {
			log.warn("[AUTH_REFRESH_TOKEN] 토큰 재발급 실패: 인증 세션 갱신 실패, userId={}, sessionId={}",
				user.getId(),
				authSession.sessionId()
			);
			throw new AuthException(AuthErrorCode.AUTH_INVALID_REFRESH_TOKEN);
		}

		log.info("[AUTH_REFRESH_TOKEN] 토큰 재발급 성공: userId={}, sessionId={}",
			user.getId(),
			authSession.sessionId()
		);

		JwtDto jwtDto = new JwtDto(
			userMapper.toDto(user),
			newAccessToken
		);

		// refresh token 쿠키 반환
		return new TokenRefreshResult(
			jwtDto,
			newRefreshToken
		);
	}

	// refresh token 만료 여부를 검증하고 만료된 세션은 삭제
	private void validateRefreshTokenNotExpired(AuthSessionData authSession, Instant refreshedAt) {
		// 미만료 세션 통과
		if (!authSession.isRefreshTokenExpired(refreshedAt)) {
			return;
		}

		// 만료 세션 삭제
		authSessionStore.delete(
			authSession.userId(),
			authSession.sessionId()
		);

		log.warn("[AUTH_REFRESH_TOKEN] 토큰 재발급 실패: 만료된 refresh token, userId={}, sessionId={}",
			authSession.userId(),
			authSession.sessionId()
		);

		// 만료 오류 반환
		throw new AuthException(AuthErrorCode.AUTH_EXPIRED_REFRESH_TOKEN);
	}

	// 최신 사용자 조회
	private User findUser(AuthSessionData authSession) {
		// 세션 사용자 조회
		return userRepository.findById(authSession.userId())
			.orElseThrow(() -> {
				// 고아 세션 삭제
				authSessionStore.delete(authSession.userId(), authSession.sessionId());

				log.warn("[AUTH_REFRESH_TOKEN] 토큰 재발급 실패: 사용자가 존재하지 않음, userId={}, sessionId={}",
					authSession.userId(),
					authSession.sessionId()
				);

				// invalid token 오류 반환
				return new AuthException(AuthErrorCode.AUTH_INVALID_REFRESH_TOKEN);
			});
	}

	// 계정 잠금 검증
	private void validateUserNotLocked(AuthSessionData authSession, User user) {
		// 활성 계정 통과
		if (!user.isLocked()) {
			return;
		}

		// 사용자 전체 세션 삭제
		authSessionStore.deleteByUserId(user.getId());

		// 잠금 실패 로그
		log.warn("[AUTH_REFRESH_TOKEN] 토큰 재발급 실패: 잠긴 계정, userId={}, sessionId={}",
			user.getId(),
			authSession.sessionId()
		);

		// 잠금 오류 반환
		throw new AuthException(AuthErrorCode.AUTH_LOCKED_ACCOUNT);
	}
}
