package com.team04.mopl.auth.security.jwt;

import java.time.Instant;
import java.util.UUID;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.stereotype.Component;

import com.team04.mopl.auth.exception.AuthErrorCode;
import com.team04.mopl.auth.exception.AuthException;
import com.team04.mopl.auth.security.MoplUserDetails;
import com.team04.mopl.user.entity.UserRole;

import lombok.RequiredArgsConstructor;

/**
 * Access Token 생성 및 검증을 담당하는 컴포넌트
 *
 * - 로그인 성공 시 사용자 정보와 sessionId를 담은 JWT 생성
 * - 요청 인증 시에는 JWT의 서명, 만료 시간, 필수 Claim을 검증하고 인증에 필요한 Claim 객체로 변환
 */
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

	private static final String SESSION_ID_CLAIM = "sid";
	private static final String EMAIL_CLAIM = "email";
	private static final String ROLE_CLAIM = "role";

	private final JwtEncoder jwtEncoder;
	private final JwtDecoder jwtDecoder;
	private final JwtProperties jwtProperties;

	// access token 만료 시각 계산
	public Instant calculateAccessExpiresAt(Instant issuedAt) {
		return issuedAt.plus(jwtProperties.accessTokenExpiration());
	}

	// refresh token 만료 시각 계산
	public Instant calculateRefreshExpiresAt(Instant issuedAt) {
		return issuedAt.plus(jwtProperties.refreshTokenExpiration());
	}

	// 로그인 성공 시 사용자 정보와 sessionId를 기반으로 access token 생성
	public String generateAccessToken(
		MoplUserDetails userDetails,
		UUID sessionId,
		Instant issuedAt,
		Instant accessExpiresAt
	) {
		JwsHeader jwsHeader = JwsHeader.with(MacAlgorithm.HS256).build();

		JwtClaimsSet claimsSet = JwtClaimsSet.builder()
			.issuer(jwtProperties.issuer())
			.subject(userDetails.getUserId().toString())
			.issuedAt(issuedAt)
			.expiresAt(accessExpiresAt)
			.claim(SESSION_ID_CLAIM, sessionId.toString())
			.claim(EMAIL_CLAIM, userDetails.getEmail())
			.claim(ROLE_CLAIM, userDetails.getRole().name())
			.build();

		return jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader, claimsSet))
			.getTokenValue();
	}

	/**
	 * access token을 검증하고 인증에 필요한 claim 추출
	 *
	 * 1. JwtDecoder가 서명과 exp 검증
	 * 2. 서비스에서 필요한 session id, role, email claim을 추가 검증
	 */
	public JwtAuthenticationClaims parseAccessToken(String accessToken) {
		try {
			Jwt jwt = jwtDecoder.decode(accessToken);

			return toAuthenticationClaims(jwt);
		} catch (AuthException exception) {
			throw exception;
		} catch (JwtValidationException exception) {
			if (isExpired(exception)) {
				throw new AuthException(AuthErrorCode.AUTH_EXPIRED_ACCESS_TOKEN, exception);
			}

			throw new AuthException(AuthErrorCode.AUTH_INVALID_ACCESS_TOKEN, exception);
		} catch (JwtException | IllegalArgumentException | NullPointerException exception) {
			throw new AuthException(AuthErrorCode.AUTH_INVALID_ACCESS_TOKEN, exception);
		}
	}

	// 검증된 JWT를 서비스 인증에 사용할 claim 객체로 변환
	private JwtAuthenticationClaims toAuthenticationClaims(Jwt jwt) {
		UUID userId = UUID.fromString(jwt.getSubject());
		UUID sessionId = UUID.fromString(getRequiredClaim(jwt, SESSION_ID_CLAIM));
		String email = getRequiredClaim(jwt, EMAIL_CLAIM);
		UserRole role = UserRole.valueOf(getRequiredClaim(jwt, ROLE_CLAIM));

		return new JwtAuthenticationClaims(
			userId,
			sessionId,
			email,
			role
		);
	}

	// JWT claim에서 필수 claim을 추출하고 유효성 검증
	private static String getRequiredClaim(Jwt jwt, String claimName) {
		Object value = jwt.getClaims().get(claimName);

		if (!(value instanceof String stringValue) || stringValue.isBlank()) {
			throw new AuthException(AuthErrorCode.AUTH_INVALID_ACCESS_TOKEN);
		}

		return stringValue;
	}

	// JWT 검증 예외가 토큰 만료로 인한 예외인지 확인
	private static boolean isExpired(JwtValidationException exception) {
		return exception.getErrors()
			.stream()
			.anyMatch(error -> JwtExpiredTokenValidator.EXPIRED_ACCESS_TOKEN_ERROR_CODE.equals(error.getErrorCode()));
	}
}
