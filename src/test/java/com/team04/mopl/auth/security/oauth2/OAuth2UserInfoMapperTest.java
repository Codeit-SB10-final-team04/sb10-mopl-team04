package com.team04.mopl.auth.security.oauth2;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
		assertThat(result.email()).isEqualTo("카카오_사용자_12345@kakao.com");
		assertThat(result.emailType()).isEqualTo(EmailType.VIRTUAL);
		assertThat(result.providerEmail()).isNull();
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
