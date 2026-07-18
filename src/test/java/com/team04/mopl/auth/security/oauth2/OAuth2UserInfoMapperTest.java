package com.team04.mopl.auth.security.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import com.team04.mopl.user.entity.EmailType;
import com.team04.mopl.user.entity.SocialProvider;

class OAuth2UserInfoMapperTest {

	@Test
	@DisplayName("Google 사용자 정보를 실제 이메일 기반으로 매핑한다")
	void map_returnGoogleUserInfo_whenGoogleAttributesProvided() {
		// given
		Map<String, Object> attributes = Map.of(
			"sub", "google-user-id",
			"email", "user@gmail.com",
			"name", "Google 사용자",
			"picture", "https://example.com/google.png"
		);

		// when
		OAuth2UserInfo result = OAuth2UserInfoMapper.map("google", attributes);

		// then
		assertThat(result.provider()).isEqualTo(SocialProvider.GOOGLE);
		assertThat(result.providerUserId()).isEqualTo("google-user-id");
		assertThat(result.email()).isEqualTo("user@gmail.com");
		assertThat(result.emailType()).isEqualTo(EmailType.REAL);
		assertThat(result.providerEmail()).isEqualTo("user@gmail.com");
	}

	@Test
	@DisplayName("Google OIDC 사용자 정보를 실제 이메일 기반으로 매핑한다")
	void mapOidc_returnGoogleUserInfo_whenGoogleOidcUserProvided() {
		// given
		OidcUser oidcUser = googleOidcUser();

		// when
		OAuth2UserInfo result = OAuth2UserInfoMapper.mapOidc("google", oidcUser);

		// then
		assertThat(result.provider()).isEqualTo(SocialProvider.GOOGLE);
		assertThat(result.providerUserId()).isEqualTo("google-user-id");
		assertThat(result.email()).isEqualTo("user@gmail.com");
		assertThat(result.emailType()).isEqualTo(EmailType.REAL);
		assertThat(result.name()).isEqualTo("Google 사용자");
		assertThat(result.profileImageUrl()).isEqualTo("https://example.com/google.png");
		assertThat(result.providerEmail()).isEqualTo("user@gmail.com");
	}

	@Test
	@DisplayName("Kakao 사용자 정보에 가상 이메일을 생성한다")
	void map_returnKakaoVirtualEmail_whenKakaoAttributesProvided() {
		// given
		Map<String, Object> attributes = Map.of(
			"id", 12345L,
			"properties", Map.of(
				"nickname", "카카오 사용자",
				"profile_image", "https://example.com/kakao.png"
			)
		);

		// when
		OAuth2UserInfo result = OAuth2UserInfoMapper.map("kakao", attributes);

		// then
		assertThat(result.provider()).isEqualTo(SocialProvider.KAKAO);
		assertThat(result.providerUserId()).isEqualTo("12345");
		assertThat(result.email()).isEqualTo("kakao_12345@kakao.com");
		assertThat(result.emailType()).isEqualTo(EmailType.VIRTUAL);
		assertThat(result.name()).isEqualTo("카카오 사용자");
		assertThat(result.providerEmail()).isNull();
	}

	@Test
	@DisplayName("지원하지 않는 소셜 제공자이면 provider_error 예외를 던진다")
	void map_throwProviderError_whenProviderIsUnsupported() {
		// given
		Map<String, Object> attributes = Map.of();

		// when & then
		assertProviderErrorThrown(() -> OAuth2UserInfoMapper.map("naver", attributes));
	}

	@Test
	@DisplayName("Google 사용자 식별자가 없으면 provider_error 예외를 던진다")
	void map_throwProviderError_whenGoogleSubjectIsMissing() {
		// given
		Map<String, Object> attributes = Map.of(
			"email", "user@gmail.com"
		);

		// when & then
		assertProviderErrorThrown(() -> OAuth2UserInfoMapper.map("google", attributes));
	}

	@Test
	@DisplayName("Google 이메일이 없으면 provider_error 예외를 던진다")
	void map_throwProviderError_whenGoogleEmailIsMissing() {
		// given
		Map<String, Object> attributes = Map.of(
			"sub", "google-user-id"
		);

		// when & then
		assertProviderErrorThrown(() -> OAuth2UserInfoMapper.map("google", attributes));
	}

	@Test
	@DisplayName("Kakao 사용자 식별자가 없으면 provider_error 예외를 던진다")
	void map_throwProviderError_whenKakaoIdIsMissing() {
		// given
		Map<String, Object> attributes = Map.of(
			"properties", Map.of("nickname", "카카오 사용자")
		);

		// when & then
		assertProviderErrorThrown(() -> OAuth2UserInfoMapper.map("kakao", attributes));
	}

	@Test
	@DisplayName("Google이 아닌 제공자를 OIDC로 매핑하면 provider_error 예외를 던진다")
	void mapOidc_throwProviderError_whenProviderIsNotGoogle() {
		// given
		OidcUser oidcUser = googleOidcUser();

		// when & then
		assertProviderErrorThrown(() -> OAuth2UserInfoMapper.mapOidc("kakao", oidcUser));
	}

	@Test
	@DisplayName("Kakao 닉네임이 비어 있으면 기본 이름과 providerUserId 기반 가상 이메일을 사용한다")
	void map_returnDefaultNameAndVirtualEmail_whenKakaoNicknameIsBlank() {
		// given
		Map<String, Object> attributes = Map.of(
			"id", 12345L,
			"properties", Map.of("nickname", "   "),
			"kakao_account", Map.of(
				"profile", Map.of("nickname", "")
			)
		);

		// when
		OAuth2UserInfo result = OAuth2UserInfoMapper.map("kakao", attributes);

		// then
		assertThat(result.name()).isEqualTo("Kakao 사용자");
		assertThat(result.email()).isEqualTo("kakao_12345@kakao.com");
	}

	private void assertProviderErrorThrown(ThrowingCallable callable) {
		assertThatThrownBy(callable)
			.isInstanceOfSatisfying(OAuth2AuthenticationException.class, exception ->
				assertThat(exception.getError().getErrorCode()).isEqualTo("provider_error")
			);
	}

	private OidcUser googleOidcUser() {
		Instant issuedAt = Instant.parse("2026-07-13T00:00:00Z");
		OidcIdToken idToken = new OidcIdToken(
			"id-token",
			issuedAt,
			issuedAt.plusSeconds(600),
			Map.of(
				"sub", "google-user-id",
				"email", "user@gmail.com",
				"name", "Google 사용자",
				"picture", "https://example.com/google.png"
			)
		);

		return new DefaultOidcUser(List.of(), idToken);
	}
}
