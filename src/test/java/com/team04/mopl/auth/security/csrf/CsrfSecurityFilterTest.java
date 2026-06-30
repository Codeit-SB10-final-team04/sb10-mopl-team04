package com.team04.mopl.auth.security.csrf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.team04.mopl.auth.security.filter.JwtAuthenticationFilter;

import jakarta.servlet.http.Cookie;

@WebMvcTest(
	controllers = CsrfSecurityFilterTest.TestController.class,
	excludeFilters = @ComponentScan.Filter(
		type = FilterType.ASSIGNABLE_TYPE,
		classes = JwtAuthenticationFilter.class // CSRF 전용 테스트이므로 JWT 필터는 제외
	)
)
@AutoConfigureMockMvc
@Import({
	CsrfSecurityFilterTest.TestController.class,
	CsrfSecurityFilterTest.TestSecurityConfig.class
})
class CsrfSecurityFilterTest { // CSRF 보안 필터 체인 테스트

	@Autowired
	private MockMvc mockMvc;

	// CSRF 필터가 실제로 XSRF-TOKEN 쿠키를 발급하는지 검증
	@Test
	@DisplayName("CSRF 토큰 조회 요청에 성공하면 XSRF-TOKEN 쿠키를 발급한다")
	void getCsrfToken_setXsrfTokenCookie_whenRequested() throws Exception {
		// when
		MvcResult result = mockMvc.perform(get("/api/auth/csrf-token"))
			.andExpect(status().isNoContent())
			.andReturn();

		// then
		Cookie csrfCookie = result.getResponse().getCookie("XSRF-TOKEN");

		assertThat(csrfCookie).isNotNull();
		assertThat(csrfCookie.getValue()).isNotBlank();
		assertThat(csrfCookie.getPath()).isEqualTo("/");
		assertThat(csrfCookie.isHttpOnly()).isFalse();
	}

	// CSRF 보호 설정이 켜져 있는지 검증
	@Test
	@DisplayName("보호된 POST 요청에 CSRF 토큰이 없으면 403 Forbidden을 반환한다")
	void protectedPost_returnForbidden_whenCsrfTokenIsMissing() throws Exception {
		// when & then
		mockMvc.perform(post("/test/protected"))
			.andExpect(status().isForbidden());
	}

	// CSRF 쿠키 값을 X-XSRF-TOKEN 헤더로 보내면 보호된 POST 요청이 통과하는지 검증
	@Test
	@DisplayName("보호된 POST 요청에 CSRF 쿠키와 헤더가 있으면 요청에 성공한다")
	void protectedPost_returnNoContent_whenCsrfCookieAndHeaderExist() throws Exception {
		// given
		MvcResult csrfResult = mockMvc.perform(get("/api/auth/csrf-token"))
			.andExpect(status().isNoContent())
			.andReturn();

		Cookie csrfCookie = csrfResult.getResponse().getCookie("XSRF-TOKEN");

		assertThat(csrfCookie).isNotNull();

		// when & then
		mockMvc.perform(post("/test/protected")
				.cookie(csrfCookie)
				.header("X-XSRF-TOKEN", csrfCookie.getValue()))
			.andExpect(status().isNoContent());
	}

	// CSRF 필터가 요청을 통과시키는지/막는지만 확인하기 위한 테스트용 컨트롤러
	@RestController
	static class TestController {

		@GetMapping("/api/auth/csrf-token")
		ResponseEntity<Void> getCsrfToken() {
			return ResponseEntity.noContent().build();
		}

		@PostMapping("/test/protected")
		ResponseEntity<Void> protectedPost() {
			return ResponseEntity.noContent().build();
		}
	}

	@TestConfiguration
	static class TestSecurityConfig {

		@Bean
		SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
			return http
				.csrf(csrf -> csrf
					.csrfTokenRepository(csrfTokenRepository())
					.csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
				.authorizeHttpRequests(authorize -> authorize
					.requestMatchers(HttpMethod.GET, "/api/auth/csrf-token").permitAll()
					.anyRequest().permitAll())
				.build();
		}

		@Bean
		CookieCsrfTokenRepository csrfTokenRepository() {
			CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();

			repository.setCookieName("XSRF-TOKEN");
			repository.setHeaderName("X-XSRF-TOKEN");
			repository.setCookiePath("/");

			return repository;
		}
	}
}
