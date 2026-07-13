package com.team04.mopl.auth.security.oauth2;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import com.team04.mopl.auth.security.MoplUserDetails;
import com.team04.mopl.user.entity.User;
import com.team04.mopl.user.service.SocialAccountService;

import lombok.extern.slf4j.Slf4j;

/**
 * OIDC 방식으로 Google 사용자 인증 정보를 확인하고 MOPL 사용자로 연결하는 서비스
 *
 * - Google 로그인은 openid scope를 사용하는 OIDC 인증 제공자로 처리
 * - Spring Security가 검증한 OIDC 사용자 정보를 내부 공통 DTO로 변환한 뒤 소셜 계정 연결 로직에 위임
 */
@Slf4j
@Service
public class MoplOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

	// 소셜 계정과 MOPL 사용자 연결 서비스
	private final SocialAccountService socialAccountService;

	// Google OIDC 사용자 정보 조회 및 검증 위임 서비스
	private final OAuth2UserService<OidcUserRequest, OidcUser> delegate;

	@Autowired
	public MoplOidcUserService(SocialAccountService socialAccountService) {
		// 운영 코드에서 사용할 기본 OIDC 서비스 구성
		this(socialAccountService, new OidcUserService());
	}

	MoplOidcUserService(
		SocialAccountService socialAccountService,
		OAuth2UserService<OidcUserRequest, OidcUser> delegate
	) {
		// 테스트 주입이 가능한 의존성 보관
		this.socialAccountService = socialAccountService;
		this.delegate = delegate;
	}

	// Google OIDC 사용자 정보 확인 후 MOPL 사용자 연결
	@Override
	public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
		// Google OIDC 사용자 정보 조회
		OidcUser oidcUser = delegate.loadUser(userRequest);

		// OIDC 사용자 정보를 내부 공통 DTO로 변환
		OAuth2UserInfo userInfo = OAuth2UserInfoMapper.mapOidc(
			userRequest.getClientRegistration().getRegistrationId(),
			oidcUser
		);

		// 소셜 계정 조회 또는 신규 사용자 생성
		User user = socialAccountService.loginOrCreate(userInfo);

		// OIDC 로그인 사용자 확인 로그 기록
		log.info(
			"[SOCIAL_LOGIN] OIDC 로그인 사용자 확인 완료: userId={}, provider={}",
			user.getId(),
			userInfo.provider()
		);

		// OIDC 정보와 MOPL principal을 함께 담은 인증 principal 반환
		return new MoplOidcUser(MoplUserDetails.from(user), oidcUser);
	}
}
