package com.team04.mopl.auth.security.oauth2;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import com.team04.mopl.auth.security.MoplUserDetails;

/**
 * Spring Security OAuth2 principal과 MOPL 사용자 principal을 함께 보관하는 어댑터
 *
 * 소셜 제공자 attributes는 OAuth2User 규약에 맞춰 유지하고,
 * 서비스 내부 인증에는 MoplUserDetails를 사용하도록 연결
 */
public class MoplOAuth2User implements OAuth2User {

	// MOPL 서비스 인증에 사용할 사용자 principal
	private final MoplUserDetails userDetails;

	// 소셜 제공자에서 받은 사용자 원본 attributes
	private final Map<String, Object> attributes;

	public MoplOAuth2User(MoplUserDetails userDetails, Map<String, Object> attributes) {
		// 내부 인증 사용자 정보 보관
		this.userDetails = userDetails;

		// 외부에서 attributes를 변경하지 못하도록 방어적 복사
		this.attributes = Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
	}

	// 성공 핸들러에서 사용할 MOPL 사용자 principal 반환
	public MoplUserDetails getUserDetails() {
		return userDetails;
	}

	// MOPL 사용자 권한 반환
	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return userDetails.getAuthorities();
	}

	// 소셜 제공자 attributes 반환
	@Override
	public Map<String, Object> getAttributes() {
		return attributes;
	}

	// Spring Security principal 이름 반환
	@Override
	public String getName() {
		return userDetails.getUserId().toString();
	}
}
