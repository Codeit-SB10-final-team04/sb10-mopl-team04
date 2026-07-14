package com.team04.mopl.auth.security.oauth2;

import com.team04.mopl.user.entity.EmailType;
import com.team04.mopl.user.entity.SocialProvider;

/**
 * 소셜 제공자별 사용자 정보를 MOPL 사용자 생성/조회에 맞게 표준화한 DTO
 *
 * Google OIDC, Kakao OAuth2처럼 응답 구조가 다른 제공자 정보를
 * SocialAccountService가 동일한 방식으로 처리할 수 있도록 정규화
 */
public record OAuth2UserInfo(
	SocialProvider provider,
	String providerUserId,
	String email,
	EmailType emailType,
	String name,
	String profileImageUrl,
	String providerEmail
) {
}
