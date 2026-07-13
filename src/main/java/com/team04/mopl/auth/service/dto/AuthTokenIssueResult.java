package com.team04.mopl.auth.service.dto;

import com.team04.mopl.auth.dto.response.JwtDto;

/**
 * 인증 성공 시 클라이언트 응답에 필요한 토큰 발급 결과
 *
 * <p>jwtDto는 JSON body로 내려갈 사용자 정보와 access token을 담고,
 * refreshToken은 HttpOnly 쿠키로 내려갈 원문 토큰을 담는다.</p>
 */
public record AuthTokenIssueResult(
	JwtDto jwtDto,
	String refreshToken
) {

	@Override
	public String toString() {
		return "AuthTokenIssueResult[jwtDto=" + jwtDto + ", refreshToken=****]";
	}
}
