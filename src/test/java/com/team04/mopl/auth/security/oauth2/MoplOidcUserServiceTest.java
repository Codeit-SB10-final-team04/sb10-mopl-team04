package com.team04.mopl.auth.security.oauth2;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import com.team04.mopl.user.entity.EmailType;
import com.team04.mopl.user.entity.SocialProvider;
import com.team04.mopl.user.entity.User;
import com.team04.mopl.user.entity.UserRole;
import com.team04.mopl.user.service.SocialAccountService;

@ExtendWith(MockitoExtension.class)
class MoplOidcUserServiceTest {

	@Mock
	private SocialAccountService socialAccountService;

	@Mock
	private OAuth2UserService<OidcUserRequest, OidcUser> delegate;

	@Mock
	private User user;

	private MoplOidcUserService moplOidcUserService;

	@BeforeEach
	void setUp() {
		moplOidcUserService = new MoplOidcUserService(socialAccountService, delegate);
	}

	@Test
	@DisplayName("Google OIDC 사용자 정보를 MOPL 사용자로 연결한다")
	void loadUser_returnMoplOidcUser_whenGoogleOidcUserProvided() {
		// given
		UUID userId = UUID.randomUUID();
		OidcIdToken idToken = googleIdToken();
		OidcUser oidcUser = new DefaultOidcUser(List.of(), idToken);
		OidcUserRequest userRequest = googleUserRequest(idToken);
		ArgumentCaptor<OAuth2UserInfo> userInfoCaptor = ArgumentCaptor.forClass(OAuth2UserInfo.class);

		given(delegate.loadUser(userRequest)).willReturn(oidcUser);
		given(socialAccountService.loginOrCreate(any(OAuth2UserInfo.class))).willReturn(user);
		given(user.getId()).willReturn(userId);
		given(user.getEmail()).willReturn("user@gmail.com");
		given(user.getName()).willReturn("Google 사용자");
		given(user.getRole()).willReturn(UserRole.USER);
		given(user.isLocked()).willReturn(false);

		// when
		OidcUser result = moplOidcUserService.loadUser(userRequest);

		// then
		verify(socialAccountService).loginOrCreate(userInfoCaptor.capture());
		OAuth2UserInfo userInfo = userInfoCaptor.getValue();
		assertThat(userInfo.provider()).isEqualTo(SocialProvider.GOOGLE);
		assertThat(userInfo.providerUserId()).isEqualTo("google-user-id");
		assertThat(userInfo.email()).isEqualTo("user@gmail.com");
		assertThat(userInfo.emailType()).isEqualTo(EmailType.REAL);
		assertThat(userInfo.name()).isEqualTo("Google 사용자");
		assertThat(userInfo.profileImageUrl()).isEqualTo("https://example.com/google.png");
		assertThat(userInfo.providerEmail()).isEqualTo("user@gmail.com");

		assertThat(result).isInstanceOf(MoplOidcUser.class);
		assertThat(result.getIdToken()).isSameAs(idToken);
		assertThat(((MoplOidcUser)result).getUserDetails().getUserId()).isEqualTo(userId);
	}

	private OidcUserRequest googleUserRequest(OidcIdToken idToken) {
		Instant issuedAt = Instant.parse("2026-07-13T00:00:00Z");
		OAuth2AccessToken accessToken = new OAuth2AccessToken(
			OAuth2AccessToken.TokenType.BEARER,
			"access-token",
			issuedAt,
			issuedAt.plusSeconds(600),
			Set.of("openid", "email", "profile")
		);

		return new OidcUserRequest(googleClientRegistration(), accessToken, idToken);
	}

	private ClientRegistration googleClientRegistration() {
		return ClientRegistration.withRegistrationId("google")
			.clientId("client-id")
			.clientSecret("client-secret")
			.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
			.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
			.redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
			.scope("openid", "email", "profile")
			.authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
			.tokenUri("https://oauth2.googleapis.com/token")
			.jwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
			.userInfoUri("https://openidconnect.googleapis.com/v1/userinfo")
			.userNameAttributeName("sub")
			.clientName("Google")
			.build();
	}

	private OidcIdToken googleIdToken() {
		Instant issuedAt = Instant.parse("2026-07-13T00:00:00Z");

		return new OidcIdToken(
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
	}
}
