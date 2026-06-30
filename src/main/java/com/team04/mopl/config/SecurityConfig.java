package com.team04.mopl.config;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.team04.mopl.auth.security.csrf.SpaCsrfTokenRequestHandler;
import com.team04.mopl.auth.security.filter.JwtAuthenticationFilter;
import com.team04.mopl.auth.security.handler.AuthSessionLogoutHandler;
import com.team04.mopl.auth.security.handler.LoginFailureHandler;
import com.team04.mopl.auth.security.handler.LoginSuccessHandler;
import com.team04.mopl.auth.security.handler.RestAccessDeniedHandler;
import com.team04.mopl.auth.security.handler.RestAuthenticationEntryPoint;
import com.team04.mopl.auth.security.handler.RestLogoutSuccessHandler;
import com.team04.mopl.auth.security.jwt.JwtExpiredTokenValidator;
import com.team04.mopl.auth.security.jwt.JwtProperties;

@EnableMethodSecurity
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

	@Bean
	public SecurityFilterChain securityFilterChain(
		HttpSecurity http,
		LoginSuccessHandler loginSuccessHandler,
		LoginFailureHandler loginFailureHandler,
		AuthSessionLogoutHandler authSessionLogoutHandler,
		RestLogoutSuccessHandler restLogoutSuccessHandler,
		JwtAuthenticationFilter jwtAuthenticationFilter,
		RestAuthenticationEntryPoint restAuthenticationEntryPoint,
		RestAccessDeniedHandler restAccessDeniedHandler
	) throws Exception {
		return http
			// CSRF 토큰 발급
			.csrf(csrf -> csrf
				.csrfTokenRepository(csrfTokenRepository())
				.csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))

			// 서버 세션을 생성하지 않도록 설정
			.sessionManagement(session -> session
				.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.requestCache(requestCache -> requestCache
				.requestCache(new NullRequestCache()))

			// 로그인 요청
			.formLogin(formLogin -> formLogin
				.loginProcessingUrl("/api/auth/sign-in")
				.usernameParameter("username")
				.passwordParameter("password")
				.successHandler(loginSuccessHandler)
				.failureHandler(loginFailureHandler))

			// 로그아웃 요청
			.logout(logout -> logout
				.logoutRequestMatcher(request ->
					"/api/auth/sign-out".equals(request.getServletPath())
						&& HttpMethod.POST.matches(request.getMethod())
				)
				.addLogoutHandler(authSessionLogoutHandler)
				.logoutSuccessHandler(restLogoutSuccessHandler)
				.clearAuthentication(true)
				.invalidateHttpSession(false) // HttpSession이 아니라 AuthSessionStore의 인증 세션을 삭제해야 함
				.permitAll())

			// 인증/인가 실패 시 ErrorResponse로 응답
			.exceptionHandling(exceptionHandling -> exceptionHandling
				.authenticationEntryPoint(restAuthenticationEntryPoint)
				.accessDeniedHandler(restAccessDeniedHandler))

			// 인증 없이 접근 허용
			.authorizeHttpRequests(authorize -> authorize
				.requestMatchers(HttpMethod.POST, "/api/users").permitAll() // 회원가입
				.requestMatchers(HttpMethod.POST, "/api/auth/sign-in").permitAll() // 로그인
				.requestMatchers(HttpMethod.POST, "/api/auth/sign-out").permitAll() // 로그아웃
				.requestMatchers(HttpMethod.POST, "/api/auth/refresh").permitAll() // 토큰 재발급
				.requestMatchers(HttpMethod.GET, "/api/auth/csrf-token").permitAll() // CSRF 토큰 조회
				.requestMatchers(
					"/swagger-ui/**",
					"/v3/api-docs/**",
					"/actuator/health"
				).permitAll()
				.anyRequest().authenticated())

			// Bearer access token 검증 필터를 username/password 로그인 필터 앞에 배치
			.addFilterBefore(
				jwtAuthenticationFilter,
				UsernamePasswordAuthenticationFilter.class
			)
			.build();
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	// Access Token 생성
	@Bean
	public JwtEncoder jwtEncoder(JwtProperties jwtProperties) {
		SecretKey secretKey = createSecretKey(jwtProperties);

		return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey));
	}

	// Access Token 검증
	@Bean
	public JwtDecoder jwtDecoder(JwtProperties jwtProperties) {
		SecretKey secretKey = createSecretKey(jwtProperties);

		NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder
			.withSecretKey(secretKey)
			.macAlgorithm(MacAlgorithm.HS256)
			.build();

		jwtDecoder.setJwtValidator(
			new DelegatingOAuth2TokenValidator<>(
				JwtValidators.createDefaultWithIssuer(jwtProperties.issuer()),
				new JwtExpiredTokenValidator()
			)
		);

		return jwtDecoder;
	}

	// CSRF 토큰을 XSRF-TOKEN 쿠키와 X-XSRF-TOKEN 헤더로 주고받도록 설정
	@Bean
	public CookieCsrfTokenRepository csrfTokenRepository() {
		CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();

		repository.setCookieName("XSRF-TOKEN");
		repository.setHeaderName("X-XSRF-TOKEN");
		repository.setCookiePath("/");

		return repository;
	}

	// JWT 서명과 검증에 사용할 SecretKey 생성
	private SecretKey createSecretKey(JwtProperties jwtProperties) {
		byte[] secretBytes = jwtProperties.secret().getBytes(StandardCharsets.UTF_8);

		return new SecretKeySpec(secretBytes, "HmacSHA256");
	}
}
