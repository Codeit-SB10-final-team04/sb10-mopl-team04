package com.team04.mopl.auth.security.csrf;

import java.util.function.Supplier;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * SPA 환경에서 CSRF 토큰을 쿠키로 내려주고, 요청 헤더의 원본 토큰 값을 검증하는 핸들러
 *
 * - 응답 쿠키에는 원본 CSRF 토큰 값을 저장
 * - 요청 헤더(X-XSRF-TOKEN) 값은 원본 토큰으로 검증
 */
public class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {

	private final CsrfTokenRequestHandler plainHandler = new CsrfTokenRequestAttributeHandler();
	private final CsrfTokenRequestHandler xorHandler = new XorCsrfTokenRequestAttributeHandler();

	// CSRF 토큰을 요청 속성에 저장하고 쿠키 생성을 위해 토큰을 강제로 로드
	@Override
	public void handle(
		HttpServletRequest request,
		HttpServletResponse response,
		Supplier<CsrfToken> csrfToken
	) {
		xorHandler.handle(request, response, csrfToken);

		csrfToken.get();
	}

	// 요청 헤더가 있으면 원본 토큰 방식으로 검증하고, 그 외에는 기본 XOR 방식으로 검증
	@Override
	public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
		String headerValue = request.getHeader(csrfToken.getHeaderName());

		if (StringUtils.hasText(headerValue)) {
			return plainHandler.resolveCsrfTokenValue(request, csrfToken);
		}

		return xorHandler.resolveCsrfTokenValue(request, csrfToken);
	}
}
