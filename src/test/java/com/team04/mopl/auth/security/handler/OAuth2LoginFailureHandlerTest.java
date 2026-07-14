package com.team04.mopl.auth.security.handler;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

import com.team04.mopl.auth.security.oauth2.OAuth2Properties;

import jakarta.servlet.http.HttpServletResponse;

class OAuth2LoginFailureHandlerTest {

	@Test
	@DisplayName("소셜 로그인 실패 시 오류 정보를 포함해 로그인 화면으로 이동한다")
	void onAuthenticationFailure_redirectsToSignInWithError() throws Exception {
		// given
		OAuth2Properties properties = new OAuth2Properties();
		OAuth2LoginFailureHandler handler = new OAuth2LoginFailureHandler(properties);
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();
		OAuth2AuthenticationException exception = new OAuth2AuthenticationException(
			new OAuth2Error("account_locked"),
			"잠긴 계정은 로그인할 수 없습니다."
		);

		// when
		handler.onAuthenticationFailure(request, response, exception);

		// then
		assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FOUND);
		assertThat(response.getRedirectedUrl())
			.isEqualTo("http://localhost:8080/#/sign-in?error=oauth_failed&error_message=account_locked");
	}

	@Test
	@DisplayName("허용된 소셜 로그인 실패 코드는 그대로 로그인 화면에 전달한다")
	void onAuthenticationFailure_redirectsWithAllowedErrorCode_whenEmailAlreadyExists() throws Exception {
		// given
		OAuth2Properties properties = new OAuth2Properties();
		OAuth2LoginFailureHandler handler = new OAuth2LoginFailureHandler(properties);
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();
		OAuth2AuthenticationException exception = new OAuth2AuthenticationException(
			new OAuth2Error("email_already_exists"),
			"동일한 이메일을 사용하는 계정이 이미 존재합니다."
		);

		// when
		handler.onAuthenticationFailure(request, response, exception);

		// then
		assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FOUND);
		assertThat(response.getRedirectedUrl())
			.isEqualTo("http://localhost:8080/#/sign-in?error=oauth_failed&error_message=email_already_exists");
	}

	@Test
	@DisplayName("허용되지 않은 OAuth2 오류 코드는 provider_error로 변환한다")
	void onAuthenticationFailure_redirectsWithProviderError_whenOAuth2ErrorCodeIsNotAllowed() throws Exception {
		// given
		OAuth2Properties properties = new OAuth2Properties();
		OAuth2LoginFailureHandler handler = new OAuth2LoginFailureHandler(properties);
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();
		OAuth2AuthenticationException exception = new OAuth2AuthenticationException(
			new OAuth2Error("access_denied"),
			"Access denied"
		);

		// when
		handler.onAuthenticationFailure(request, response, exception);

		// then
		assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FOUND);
		assertThat(response.getRedirectedUrl())
			.isEqualTo("http://localhost:8080/#/sign-in?error=oauth_failed&error_message=provider_error");
	}

	@Test
	@DisplayName("OAuth2 예외가 아니면 provider_error로 변환한다")
	void onAuthenticationFailure_redirectsWithProviderError_whenExceptionIsNotOAuth2Exception() throws Exception {
		// given
		OAuth2Properties properties = new OAuth2Properties();
		OAuth2LoginFailureHandler handler = new OAuth2LoginFailureHandler(properties);
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();
		AuthenticationServiceException exception = new AuthenticationServiceException("internal error");

		// when
		handler.onAuthenticationFailure(request, response, exception);

		// then
		assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FOUND);
		assertThat(response.getRedirectedUrl())
			.isEqualTo("http://localhost:8080/#/sign-in?error=oauth_failed&error_message=provider_error");
	}

	@Test
	@DisplayName("실패 리다이렉트 URI에 기존 쿼리가 있으면 오류 정보를 추가 파라미터로 붙인다")
	void onAuthenticationFailure_appendsErrorParameters_whenFailureRedirectUriHasQuery() throws Exception {
		// given
		OAuth2Properties properties = new OAuth2Properties();
		properties.setFailureRedirectUri("http://localhost:8080/#/sign-in?from=oauth");
		OAuth2LoginFailureHandler handler = new OAuth2LoginFailureHandler(properties);
		MockHttpServletRequest request = new MockHttpServletRequest();
		MockHttpServletResponse response = new MockHttpServletResponse();
		OAuth2AuthenticationException exception = new OAuth2AuthenticationException(
			new OAuth2Error("provider_error"),
			"제공자 응답을 처리할 수 없습니다."
		);

		// when
		handler.onAuthenticationFailure(request, response, exception);

		// then
		assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FOUND);
		assertThat(response.getRedirectedUrl())
			.isEqualTo("http://localhost:8080/#/sign-in?from=oauth&error=oauth_failed&error_message=provider_error");
	}
}
