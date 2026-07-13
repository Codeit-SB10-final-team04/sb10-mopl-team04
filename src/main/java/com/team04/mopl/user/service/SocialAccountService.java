package com.team04.mopl.user.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team04.mopl.auth.security.oauth2.OAuth2UserInfo;
import com.team04.mopl.user.entity.SocialAccount;
import com.team04.mopl.user.entity.User;
import com.team04.mopl.user.repository.SocialAccountRepository;
import com.team04.mopl.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class SocialAccountService {

	// 소셜 계정 연결 정보 저장소
	private final SocialAccountRepository socialAccountRepository;

	// MOPL 사용자 저장소
	private final UserRepository userRepository;

	// 연결된 소셜 계정은 기존 사용자를 반환하고, 처음 로그인한 계정은 사용자와 연결 정보를 생성
	@Transactional
	public User loginOrCreate(OAuth2UserInfo userInfo) {
		// 제공자와 제공자 사용자 ID 기준 기존 소셜 계정 조회
		return socialAccountRepository.findByProviderAndProviderUserId(
				userInfo.provider(),
				userInfo.providerUserId()
			)
			// 기존 연결 계정의 MOPL 사용자 추출
			.map(SocialAccount::getUser)
			// 잠금 계정 로그인 차단
			.map(this::validateUserCanLogin)
			// 연결 정보가 없으면 신규 사용자와 소셜 계정 생성
			.orElseGet(() -> createSocialUser(userInfo));
	}

	// 신규 소셜 사용자와 소셜 계정 연결 정보 생성
	private User createSocialUser(OAuth2UserInfo userInfo) {
		// 동일 이메일 기존 계정 존재 여부 확인
		if (userRepository.existsByEmail(userInfo.email())) {
			throw emailAlreadyExistsException();
		}

		// 소셜 사용자 정보 기반 MOPL 사용자 생성
		User user = User.builder()
			.name(userInfo.name())
			.email(userInfo.email())
			.emailType(userInfo.emailType())
			.profileImageUrl(userInfo.profileImageUrl())
			.build();

		// 사용자 우선 저장
		User savedUser = saveUser(user);

		// 저장된 사용자 기준 소셜 계정 연결 정보 생성
		SocialAccount socialAccount = SocialAccount.builder()
			.user(savedUser)
			.provider(userInfo.provider())
			.providerUserId(userInfo.providerUserId())
			.providerEmail(userInfo.providerEmail())
			.build();

		// 소셜 계정 연결 정보 저장
		socialAccountRepository.saveAndFlush(socialAccount);

		// 소셜 계정 생성 완료 로그 기록
		log.info(
			"[SOCIAL_ACCOUNT_CREATE] 소셜 계정 연결 완료: userId={}, provider={}",
			savedUser.getId(),
			userInfo.provider()
		);

		// 생성된 MOPL 사용자 반환
		return savedUser;
	}

	// 신규 사용자 저장 및 이메일 유니크 제약 충돌 변환
	private User saveUser(User user) {
		try {
			// 사용자 저장 후 즉시 flush하여 이메일 중복을 현재 흐름에서 감지
			return userRepository.saveAndFlush(user);
		} catch (DataIntegrityViolationException exception) {
			// 동시 가입 요청에서 발생한 이메일 중복 충돌 변환
			throw emailAlreadyExistsException();
		}
	}

	// 소셜 로그인 가능한 사용자 상태 검증
	private User validateUserCanLogin(User user) {
		if (!user.isLocked()) {
			// 잠기지 않은 사용자 로그인 허용
			return user;
		}

		// 잠금 계정 로그인 차단
		throw oauthException("account_locked", "잠긴 계정은 로그인할 수 없습니다.");
	}

	// 소셜 로그인 실패 핸들러로 전달할 OAuth2 예외 생성
	private OAuth2AuthenticationException oauthException(String errorCode, String message) {
		return new OAuth2AuthenticationException(new OAuth2Error(errorCode), message);
	}

	// 이메일 중복 OAuth2 예외 생성
	private OAuth2AuthenticationException emailAlreadyExistsException() {
		return oauthException(
			"email_already_exists",
			"동일한 이메일을 사용하는 계정이 이미 존재합니다."
		);
	}
}
