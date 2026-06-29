package com.team04.mopl.auth.security.jwt;

import java.util.UUID;

import com.team04.mopl.user.entity.UserRole;

/**
 * Access Token에서 추출한 인증 Claim 정보를 담는 record
 *
 * JWT 검증 후 SecurityContext에 인증 객체를 만들기 위해 필요한 정보들 표현
 */
public record JwtAuthenticationClaims(
	UUID userId,
	UUID sessionId,
	String email,
	UserRole role
) {
}
