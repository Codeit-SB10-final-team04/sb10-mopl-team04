package com.team04.mopl.config;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;

/*
    MDC Logging Filter
    ------------------
    모든 HTTP 요청에 대해 고유한 로그 이름표(Request ID)를 부여하는 필터
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)        // Security 보다 우선적으로 수행
public class MdcLoggingFilter extends OncePerRequestFilter {

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
		throws ServletException, IOException {

		// 고유한 Request ID 생성 (하이픈 제거)
		String requestId = UUID.randomUUID().toString().replace("-", "");

		try {
			// MDC(Mapped Diagnostic Context)에 정보 담기
			MDC.put("request_id", requestId);
			MDC.put("request_method", request.getMethod());
			MDC.put("request_uri", request.getRequestURI());

			// 응답 헤더에 Request ID 추가
			response.setHeader("Mople-Request-ID", requestId);

			// 다음 필터로 요청 및 응답 객체 전달
			filterChain.doFilter(request, response);

		} finally {
			// 메모리 누수 방지를 위한 초기화
			MDC.clear();
		}
	}
}