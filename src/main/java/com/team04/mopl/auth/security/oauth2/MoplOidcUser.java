package com.team04.mopl.auth.security.oauth2;

import java.util.Map;

import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcUserInfo;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import com.team04.mopl.auth.security.MoplUserDetails;

/**
 * Google OIDC principal과 MOPL 사용자 principal을 함께 보관하는 어댑터
 *
 * - MoplOAuth2User 구조를 재사용해 성공 핸들러의 공통 처리 유지
 * - OIDC 표준 객체가 제공하는 id token, user info, claims 접근도 그대로 위임
 */
public class MoplOidcUser extends MoplOAuth2User implements OidcUser {

	// OIDC 표준 정보 접근을 위한 원본 principal
	private final OidcUser delegate;

	public MoplOidcUser(MoplUserDetails userDetails, OidcUser delegate) {
		// 공통 OAuth2 principal 초기화
		super(userDetails, delegate.getAttributes());

		// OIDC 전용 정보 위임 대상 보관
		this.delegate = delegate;
	}

	// OIDC claims 반환
	@Override
	public Map<String, Object> getClaims() {
		return delegate.getClaims();
	}

	// OIDC user info 반환
	@Override
	public OidcUserInfo getUserInfo() {
		return delegate.getUserInfo();
	}

	// OIDC ID token 반환
	@Override
	public OidcIdToken getIdToken() {
		return delegate.getIdToken();
	}
}
