package com.team04.mopl.auth.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.team04.mopl.auth.exception.AuthErrorCode;
import com.team04.mopl.auth.exception.AuthException;
import com.team04.mopl.auth.security.MoplUserDetails;
import com.team04.mopl.user.entity.UserRole;

class JwtTokenProviderTest {

	private static final String SECRET = "test-secret-key-must-be-at-least-32-bytes";
	private static final String ISSUER = "mopl";
	private static final String EMAIL = "test@test.com";

	private final JwtProperties jwtProperties = new JwtProperties(
		SECRET,
		ISSUER,
		1800,
		1209600,
		"REFRESH_TOKEN",
		false,
		"Lax"
	);

	private final SecretKey secretKey = new SecretKeySpec(
		SECRET.getBytes(StandardCharsets.UTF_8),
		"HmacSHA256"
	);

	private final JwtEncoder jwtEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(secretKey));
	private final JwtDecoder jwtDecoder = createJwtDecoder();

	private final JwtTokenProvider jwtTokenProvider = new JwtTokenProvider(
		jwtEncoder,
		jwtDecoder,
		jwtProperties
	);

	@Test
	@DisplayName("Access Token 만료 시각을 계산한다")
	void calculateAccessExpiresAt_returnsExpirationTime_whenIssuedAtProvided() {
		// given
		Instant issuedAt = Instant.parse("2026-01-01T00:00:00Z");

		// when
		Instant expiresAt = jwtTokenProvider.calculateAccessExpiresAt(issuedAt);

		// then
		assertThat(expiresAt).isEqualTo(Instant.parse("2026-01-01T00:30:00Z"));
	}

	@Test
	@DisplayName("Refresh Token 만료 시각을 계산한다")
	void calculateRefreshExpiresAt_returnsExpirationTime_whenIssuedAtProvided() {
		// given
		Instant issuedAt = Instant.parse("2026-01-01T00:00:00Z");

		// when
		Instant expiresAt = jwtTokenProvider.calculateRefreshExpiresAt(issuedAt);

		// then
		assertThat(expiresAt).isEqualTo(Instant.parse("2026-01-15T00:00:00Z"));
	}

	@Test
	@DisplayName("Access Token을 생성하고 인증 Claim을 추출한다")
	void parseAccessToken_returnsClaims_whenAccessTokenIsValid() {
		// given
		UUID userId = UUID.randomUUID();
		UUID sessionId = UUID.randomUUID();
		Instant issuedAt = Instant.now();
		Instant expiresAt = jwtTokenProvider.calculateAccessExpiresAt(issuedAt);

		MoplUserDetails userDetails = MoplUserDetails.authenticated(
			userId,
			EMAIL,
			UserRole.USER
		);

		String accessToken = jwtTokenProvider.generateAccessToken(
			userDetails,
			sessionId,
			issuedAt,
			expiresAt
		);

		// when
		JwtAuthenticationClaims claims = jwtTokenProvider.parseAccessToken(accessToken);

		// then
		assertThat(claims.userId()).isEqualTo(userId);
		assertThat(claims.sessionId()).isEqualTo(sessionId);
		assertThat(claims.email()).isEqualTo(EMAIL);
		assertThat(claims.role()).isEqualTo(UserRole.USER);
	}

	@Test
	@DisplayName("잘못된 Access Token이면 인증 예외가 발생한다")
	void parseAccessToken_throwsException_whenAccessTokenIsInvalid() {
		// given
		String invalidToken = "invalid.token.value";

		// when & then
		assertThatThrownBy(() -> jwtTokenProvider.parseAccessToken(invalidToken))
			.isInstanceOf(AuthException.class)
			.extracting("errorCode")
			.isEqualTo(AuthErrorCode.AUTH_INVALID_ACCESS_TOKEN);
	}

	@Test
	@DisplayName("만료된 Access Token이면 만료 예외가 발생한다")
	void parseAccessToken_throwsException_whenAccessTokenIsExpired() {
		// given
		UUID userId = UUID.randomUUID();
		UUID sessionId = UUID.randomUUID();
		Instant issuedAt = Instant.now().minusSeconds(7200);
		Instant expiresAt = Instant.now().minusSeconds(3600);

		MoplUserDetails userDetails = MoplUserDetails.authenticated(
			userId,
			EMAIL,
			UserRole.USER
		);

		String expiredAccessToken = jwtTokenProvider.generateAccessToken(
			userDetails,
			sessionId,
			issuedAt,
			expiresAt
		);

		// when & then
		assertThatThrownBy(() -> jwtTokenProvider.parseAccessToken(expiredAccessToken))
			.isInstanceOf(AuthException.class)
			.extracting("errorCode")
			.isEqualTo(AuthErrorCode.AUTH_EXPIRED_ACCESS_TOKEN);
	}

	@Test
	@DisplayName("필수 Claim이 없으면 인증 예외가 발생한다")
	void parseAccessToken_throwsException_whenRequiredClaimIsMissing() {
		// given
		JwtClaimsSet claimsSet = JwtClaimsSet.builder()
			.issuer(ISSUER)
			.subject(UUID.randomUUID().toString())
			.issuedAt(Instant.now())
			.expiresAt(Instant.now().plusSeconds(1800))
			.claim("email", EMAIL)
			.claim("role", UserRole.USER.name())
			.build();

		String accessToken = encode(claimsSet);

		// when & then
		assertThatThrownBy(() -> jwtTokenProvider.parseAccessToken(accessToken))
			.isInstanceOf(AuthException.class)
			.extracting("errorCode")
			.isEqualTo(AuthErrorCode.AUTH_INVALID_ACCESS_TOKEN);
	}

	@Test
	@DisplayName("Role Claim이 잘못되면 인증 예외가 발생한다")
	void parseAccessToken_throwsException_whenRoleClaimIsInvalid() {
		// given
		JwtClaimsSet claimsSet = JwtClaimsSet.builder()
			.issuer(ISSUER)
			.subject(UUID.randomUUID().toString())
			.issuedAt(Instant.now())
			.expiresAt(Instant.now().plusSeconds(1800))
			.claim("sid", UUID.randomUUID().toString())
			.claim("email", EMAIL)
			.claim("role", "INVALID_ROLE")
			.build();

		String accessToken = encode(claimsSet);

		// when & then
		assertThatThrownBy(() -> jwtTokenProvider.parseAccessToken(accessToken))
			.isInstanceOf(AuthException.class)
			.extracting("errorCode")
			.isEqualTo(AuthErrorCode.AUTH_INVALID_ACCESS_TOKEN);
	}

	private JwtDecoder createJwtDecoder() {
		NimbusJwtDecoder decoder = NimbusJwtDecoder
			.withSecretKey(secretKey)
			.macAlgorithm(MacAlgorithm.HS256)
			.build();

		decoder.setJwtValidator(
			new DelegatingOAuth2TokenValidator<>(
				JwtValidators.createDefaultWithIssuer(jwtProperties.issuer()),
				new JwtExpiredTokenValidator()
			)
		);

		return decoder;
	}

	private String encode(JwtClaimsSet claimsSet) {
		JwsHeader jwsHeader = JwsHeader.with(MacAlgorithm.HS256).build();

		return jwtEncoder.encode(JwtEncoderParameters.from(jwsHeader, claimsSet))
			.getTokenValue();
	}
}