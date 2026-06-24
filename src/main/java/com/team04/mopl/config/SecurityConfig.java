package com.team04.mopl.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		return http
			// 인증 기능 구현 전까지는 CSRF 검증 비활성화
			.csrf(AbstractHttpConfigurer::disable)

			// Spring Security의 기본 로그인 페이지를 사용하지 않기 위해 비활성화
			.formLogin(AbstractHttpConfigurer::disable)

			// 서버 세션을 생성하지 않도록 설정
			.sessionManagement(session -> session
				.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

			// 인증 기능 구현 전까지는 모든 요청 허용
			.authorizeHttpRequests(authorize -> authorize
				.anyRequest().permitAll()
			)
			.build();
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}
}
