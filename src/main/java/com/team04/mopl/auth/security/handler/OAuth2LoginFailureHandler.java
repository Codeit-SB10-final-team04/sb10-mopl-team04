package com.team04.mopl.auth.security.handler;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import com.team04.mopl.auth.security.oauth2.OAuth2Properties;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 소셜 로그인 실패 원인을 프론트엔드가 처리할 수 있는 쿼리 파라미터로 변환하는 핸들러
 *
 * - 제공자 오류나 도메인 검증 실패가 그대로 노출되지 않도록 허용된 오류 코드만 전달
 * - 최종 응답은 로그인 화면으로 리다이렉트하며, 프론트엔드는 error와 error_message 값을 사용해 토스트를 표시
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

	private final OAuth2Properties oauth2Properties;

	// 소셜 로그인 실패 정보를 안전한 오류 코드로 변환하여 로그인 화면으로 이동
	@Override
	public void onAuthenticationFailure(
		HttpServletRequest request,
		HttpServletResponse response,
		AuthenticationException exception
	) throws IOException {
		// 프론트엔드에 전달 가능한 오류 메시지 코드 변환
		String errorMessage = resolveErrorMessage(exception);

		// 실패 리다이렉트 URI 생성
		String redirectUri = appendFailureParameters(
			oauth2Properties.getFailureRedirectUri(),
			"oauth_failed",
			errorMessage
		);

		// 소셜 로그인 실패 로그 기록
		log.warn("[SOCIAL_LOGIN] 소셜 로그인 실패: errorMessage={}", errorMessage);

		// 로그인 화면 리다이렉트
		response.sendRedirect(redirectUri);
	}

	// 허용된 OAuth2 오류 코드 변환
	private String resolveErrorMessage(AuthenticationException exception) {
		if (exception instanceof OAuth2AuthenticationException oauth2Exception) {
			// 도메인 서비스에서 전달한 OAuth2 오류 코드 추출
			String errorCode = oauth2Exception.getError().getErrorCode();

			// 프론트엔드에 노출할 수 있는 오류 코드 선별
			return switch (errorCode) {
				case "account_locked", "email_already_exists", "provider_error" -> errorCode;
				default -> "provider_error";
			};
		}

		// 알 수 없는 인증 실패 기본값 처리
		return "provider_error";
	}

	// 실패 리다이렉트 URI에 오류 쿼리 파라미터 추가
	private String appendFailureParameters(String redirectUri, String error, String errorMessage) {
		// 기존 쿼리 파라미터 존재 여부에 따른 구분자 선택
		String separator = redirectUri.contains("?") ? "&" : "?";

		// URL 인코딩된 오류 파라미터 조립
		return redirectUri
			+ separator
			+ "error="
			+ encode(error)
			+ "&error_message="
			+ encode(errorMessage);
	}

	// 쿼리 파라미터 값 URL 인코딩
	private String encode(String value) {
		return URLEncoder.encode(value, StandardCharsets.UTF_8);
	}
}
