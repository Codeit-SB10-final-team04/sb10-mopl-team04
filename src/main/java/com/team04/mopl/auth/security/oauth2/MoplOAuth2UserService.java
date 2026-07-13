package com.team04.mopl.auth.security.oauth2;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import com.team04.mopl.auth.security.MoplUserDetails;
import com.team04.mopl.user.entity.User;
import com.team04.mopl.user.service.SocialAccountService;

import lombok.extern.slf4j.Slf4j;

/**
 * OAuth2 UserInfo 방식으로 소셜 사용자 정보를 조회하고 MOPL 사용자로 연결하는 서비스
 *
 * - Kakao 로그인 사용자 정보 처리
 * - 제공자별 attributes를 공통 OAuth2UserInfo로 변환한 뒤 사용자와 소셜 계정을 생성하거나 조회
 */
@Slf4j
@Service
public class MoplOAuth2UserService implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

	// 소셜 계정과 MOPL 사용자 연결 서비스
	private final SocialAccountService socialAccountService;

	// 제공자 UserInfo 엔드포인트 호출용 기본 서비스
	private final OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate;

	@Autowired
	public MoplOAuth2UserService(SocialAccountService socialAccountService) {
		// 운영 코드에서 사용할 기본 OAuth2 UserInfo 서비스 구성
		this(socialAccountService, new DefaultOAuth2UserService());
	}

	MoplOAuth2UserService(
		SocialAccountService socialAccountService,
		OAuth2UserService<OAuth2UserRequest, OAuth2User> delegate
	) {
		// 테스트 주입이 가능한 의존성 보관
		this.socialAccountService = socialAccountService;
		this.delegate = delegate;
	}

	// 제공자 사용자 정보를 표준화하고 MOPL 사용자로 연결
	@Override
	public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
		// 제공자 UserInfo 엔드포인트 사용자 정보 조회
		OAuth2User oauth2User = delegate.loadUser(userRequest);

		// 제공자별 attributes를 내부 공통 DTO로 변환
		OAuth2UserInfo userInfo = OAuth2UserInfoMapper.map(
			userRequest.getClientRegistration().getRegistrationId(),
			oauth2User.getAttributes()
		);

		// 소셜 계정 조회 또는 신규 사용자 생성
		User user = socialAccountService.loginOrCreate(userInfo);

		// 소셜 로그인 사용자 확인 로그 기록
		log.info(
			"[SOCIAL_LOGIN] 소셜 로그인 사용자 확인 완료: userId={}, provider={}",
			user.getId(),
			userInfo.provider()
		);

		// Spring Security 인증 principal 반환
		return new MoplOAuth2User(MoplUserDetails.from(user), oauth2User.getAttributes());
	}
}
