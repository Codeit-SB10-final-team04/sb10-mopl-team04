package com.team04.mopl.auth.security.handler;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;

import com.team04.mopl.auth.security.oauth2.OAuth2Properties;

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
		assertThat(response.getRedirectedUrl())
			.isEqualTo("http://localhost:8080/#/sign-in?error=oauth_failed&error_message=account_locked");
	}
}
