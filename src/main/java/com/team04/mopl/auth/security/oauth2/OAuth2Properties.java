package com.team04.mopl.auth.security.oauth2;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * 소셜 로그인 성공/실패 후 이동할 프론트엔드 리다이렉트 URI 설정
 *
 * Spring Security의 제공자 콜백 URI가 아니라,
 * MOPL 백엔드가 인증 처리를 끝낸 뒤 브라우저를 돌려보낼 화면 주소를 관리
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "mopl.oauth2")
public class OAuth2Properties {

	// 소셜 로그인 성공 후 이동할 프론트엔드 URI
	private String successRedirectUri = "http://localhost:8080/#/contents";

	// 소셜 로그인 실패 후 이동할 프론트엔드 URI
	private String failureRedirectUri = "http://localhost:8080/#/sign-in";
}
