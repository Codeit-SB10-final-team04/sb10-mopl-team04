package com.team04.mopl.auth.security.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;

import com.team04.mopl.user.entity.EmailType;
import com.team04.mopl.user.entity.SocialProvider;
import com.team04.mopl.user.entity.User;
import com.team04.mopl.user.entity.UserRole;
import com.team04.mopl.user.service.SocialAccountService;

@ExtendWith(MockitoExtension.class)
class MoplOAuth2UserServiceTest {

	@Mock
	private SocialAccountService socialAccountService;

	@Mock
	private OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate;

	@Mock
	private User user;

	private MoplOAuth2UserService moplOAuth2UserService;

	@BeforeEach
	void setUp() {
		moplOAuth2UserService = new MoplOAuth2UserService(socialAccountService, delegate);
	}

	@Test
	@DisplayName("Kakao OAuth2 사용자 정보를 MOPL 사용자로 연결한다")
	void loadUser_returnMoplOAuth2User_whenKakaoUserInfoProvided() {
		// given
		UUID userId = UUID.randomUUID();
		Map<String, Object> attributes = kakaoAttributes();
		OAuth2User oauth2User = new DefaultOAuth2User(
			List.of(new SimpleGrantedAuthority("ROLE_USER")),
			attributes,
			"id"
		);
		OAuth2UserRequest userRequest = kakaoUserRequest();
		ArgumentCaptor<OAuth2UserInfo> userInfoCaptor = ArgumentCaptor.forClass(OAuth2UserInfo.class);

		given(delegate.loadUser(userRequest)).willReturn(oauth2User);
		given(socialAccountService.loginOrCreate(any(OAuth2UserInfo.class))).willReturn(user);
		given(user.getId()).willReturn(userId);
		given(user.getEmail()).willReturn("kakao_12345@kakao.com");
		given(user.getName()).willReturn("카카오 사용자");
		given(user.getRole()).willReturn(UserRole.USER);
		given(user.isLocked()).willReturn(false);

		// when
		OAuth2User result = moplOAuth2UserService.loadUser(userRequest);

		// then
		verify(socialAccountService).loginOrCreate(userInfoCaptor.capture());
		OAuth2UserInfo userInfo = userInfoCaptor.getValue();
		assertThat(userInfo.provider()).isEqualTo(SocialProvider.KAKAO);
		assertThat(userInfo.providerUserId()).isEqualTo("12345");
		assertThat(userInfo.email()).isEqualTo("kakao_12345@kakao.com");
		assertThat(userInfo.emailType()).isEqualTo(EmailType.VIRTUAL);
		assertThat(userInfo.name()).isEqualTo("카카오 사용자");
		assertThat(userInfo.profileImageUrl()).isEqualTo("https://example.com/kakao.png");
		assertThat(userInfo.providerEmail()).isNull();

		assertThat(result).isInstanceOf(MoplOAuth2User.class);
		assertThat(result.getAttributes()).isEqualTo(attributes);
		assertThat(((MoplOAuth2User)result).getUserDetails().getUserId()).isEqualTo(userId);
	}

	@Test
	@DisplayName("OAuth2 사용자 정보 조회에 실패하면 예외를 그대로 전파한다")
	void loadUser_throwOAuth2AuthenticationException_whenDelegateFails() {
		// given
		OAuth2UserRequest userRequest = kakaoUserRequest();
		OAuth2AuthenticationException exception = new OAuth2AuthenticationException(
			new OAuth2Error("provider_error"),
			"제공자 사용자 정보를 조회할 수 없습니다."
		);

		given(delegate.loadUser(userRequest)).willThrow(exception);

		// when & then
		assertThatThrownBy(() -> moplOAuth2UserService.loadUser(userRequest))
			.isSameAs(exception);
	}

	private OAuth2UserRequest kakaoUserRequest() {
		Instant issuedAt = Instant.parse("2026-07-13T00:00:00Z");
		OAuth2AccessToken accessToken = new OAuth2AccessToken(
			OAuth2AccessToken.TokenType.BEARER,
			"access-token",
			issuedAt,
			issuedAt.plusSeconds(600),
			Set.of("profile_nickname", "profile_image")
		);

		return new OAuth2UserRequest(kakaoClientRegistration(), accessToken);
	}

	private ClientRegistration kakaoClientRegistration() {
		return ClientRegistration.withRegistrationId("kakao")
			.clientId("client-id")
			.clientSecret("client-secret")
			.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
			.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
			.redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
			.scope("profile_nickname", "profile_image")
			.authorizationUri("https://kauth.kakao.com/oauth/authorize")
			.tokenUri("https://kauth.kakao.com/oauth/token")
			.userInfoUri("https://kapi.kakao.com/v2/user/me")
			.userNameAttributeName("id")
			.clientName("Kakao")
			.build();
	}

	private Map<String, Object> kakaoAttributes() {
		return Map.of(
			"id", "12345",
			"properties", Map.of(
				"nickname", "카카오 사용자",
				"profile_image", "https://example.com/kakao.png"
			)
		);
	}
}
