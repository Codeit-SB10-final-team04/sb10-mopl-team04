package com.team04.mopl.auth.security.oauth2;

import java.util.Locale;
import java.util.Map;

import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import com.team04.mopl.user.entity.EmailType;
import com.team04.mopl.user.entity.SocialProvider;

/**
 * 소셜 제공자별 사용자 응답을 MOPL 공통 소셜 사용자 정보로 변환하는 매퍼
 *
 * - Google은 OAuth2 UserInfo와 OIDC 사용자 정보를 모두 지원
 * - Kakao는 이메일을 제공하지 않는 요구사항에 맞춰 가상 이메일 생성
 */
public final class OAuth2UserInfoMapper {

	// 제공자 응답 검증 실패 시 소셜 로그인 실패 핸들러로 전달할 공통 오류 코드
	private static final String PROVIDER_ERROR = "provider_error";

	private OAuth2UserInfoMapper() {
	}

	// OAuth2 UserInfo attributes 기반 사용자 정보 매핑
	public static OAuth2UserInfo map(String registrationId, Map<String, Object> attributes) {
		// registrationId 기반 소셜 제공자 식별
		SocialProvider provider = resolveProvider(registrationId);

		// 제공자별 attributes 구조에 맞는 변환 분기
		return switch (provider) {
			case GOOGLE -> mapGoogle(attributes);
			case KAKAO -> mapKakao(attributes);
		};
	}

	// OIDC principal 기반 사용자 정보 매핑
	public static OAuth2UserInfo mapOidc(String registrationId, OidcUser oidcUser) {
		// registrationId 기반 소셜 제공자 식별
		SocialProvider provider = resolveProvider(registrationId);

		// 현재 OIDC 흐름은 Google만 허용
		if (provider != SocialProvider.GOOGLE) {
			throw providerException("OIDC를 지원하지 않는 소셜 로그인 제공자입니다.", null);
		}

		// Google OIDC 사용자 정보 변환
		return mapGoogle(oidcUser);
	}

	// Google OAuth2 UserInfo attributes 변환
	private static OAuth2UserInfo mapGoogle(Map<String, Object> attributes) {
		// Google 고유 사용자 식별자 추출
		String providerUserId = requiredString(attributes.get("sub"));

		// Google 실제 이메일 추출
		String email = requiredString(attributes.get("email"));

		// Google 프로필 이름 추출
		String name = valueOrDefault(attributes.get("name"), "Google 사용자");

		// Google 프로필 이미지 URL 추출
		String profileImageUrl = optionalString(attributes.get("picture"));

		// MOPL 공통 소셜 사용자 정보 생성
		return new OAuth2UserInfo(
			SocialProvider.GOOGLE,
			providerUserId,
			email,
			EmailType.REAL,
			name,
			profileImageUrl,
			email
		);
	}

	// Google OIDC 사용자 정보 변환
	private static OAuth2UserInfo mapGoogle(OidcUser oidcUser) {
		// OIDC subject 기반 Google 고유 사용자 식별자 추출
		String providerUserId = requiredString(oidcUser.getSubject());

		// OIDC email claim 기반 실제 이메일 추출
		String email = requiredString(oidcUser.getEmail());

		// OIDC name claim 기반 프로필 이름 추출
		String name = valueOrDefault(oidcUser.getFullName(), "Google 사용자");

		// OIDC picture claim 기반 프로필 이미지 URL 추출
		String profileImageUrl = optionalString(oidcUser.getPicture());

		// MOPL 공통 소셜 사용자 정보 생성
		return new OAuth2UserInfo(
			SocialProvider.GOOGLE,
			providerUserId,
			email,
			EmailType.REAL,
			name,
			profileImageUrl,
			email
		);
	}

	// Kakao OAuth2 UserInfo attributes 변환
	private static OAuth2UserInfo mapKakao(Map<String, Object> attributes) {
		// Kakao 고유 사용자 식별자 추출
		String providerUserId = requiredString(attributes.get("id"));

		// Kakao properties 객체 추출
		Map<String, Object> properties = nestedMap(attributes.get("properties"));

		// Kakao account 객체 추출
		Map<String, Object> kakaoAccount = nestedMap(attributes.get("kakao_account"));

		// Kakao profile 객체 추출
		Map<String, Object> profile = nestedMap(kakaoAccount.get("profile"));

		// Kakao 닉네임 후보 값 선택
		String nickname = firstNonBlank(
			optionalString(properties.get("nickname")),
			optionalString(profile.get("nickname")),
			"Kakao 사용자"
		);

		// Kakao 프로필 이미지 후보 값 선택
		String profileImageUrl = firstNonBlank(
			optionalString(properties.get("profile_image")),
			optionalString(profile.get("profile_image_url")),
			null
		);

		// Kakao 이메일 미제공 정책 대응용 가상 이메일 생성
		String virtualEmail = createKakaoVirtualEmail(providerUserId);

		// MOPL 공통 소셜 사용자 정보 생성
		return new OAuth2UserInfo(
			SocialProvider.KAKAO,
			providerUserId,
			virtualEmail,
			EmailType.VIRTUAL,
			nickname,
			profileImageUrl,
			null
		);
	}

	// registrationId를 소셜 제공자 enum으로 변환
	private static SocialProvider resolveProvider(String registrationId) {
		try {
			// Spring Security registrationId 대소문자 차이 보정
			return SocialProvider.valueOf(registrationId.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException | NullPointerException exception) {
			// 지원하지 않는 제공자 오류 변환
			throw providerException("지원하지 않는 소셜 로그인 제공자입니다.", exception);
		}
	}

	// 필수 문자열 값 추출
	private static String requiredString(Object value) {
		// 공백 제거된 문자열 변환
		String stringValue = optionalString(value);

		// 필수 값 누락 검증
		if (stringValue == null) {
			throw providerException("소셜 사용자 식별 정보가 없습니다.", null);
		}

		// 검증된 문자열 반환
		return stringValue;
	}

	// 선택 문자열 값 추출
	private static String optionalString(Object value) {
		if (value == null) {
			// 값 없음 처리
			return null;
		}

		// 문자열 변환 후 앞뒤 공백 제거
		String stringValue = String.valueOf(value).trim();

		// 빈 문자열 정규화
		return stringValue.isBlank() ? null : stringValue;
	}

	// 문자열 값 또는 기본값 반환
	private static String valueOrDefault(Object value, String defaultValue) {
		// 선택 문자열 변환
		String stringValue = optionalString(value);

		// 값이 없을 때 기본값 사용
		return stringValue == null ? defaultValue : stringValue;
	}

	// 중첩 Map 값 추출
	@SuppressWarnings("unchecked")
	private static Map<String, Object> nestedMap(Object value) {
		if (value instanceof Map<?, ?> map) {
			// 제공자 응답 중첩 객체 변환
			return (Map<String, Object>)map;
		}

		// 중첩 객체 없음 처리
		return Map.of();
	}

	// 우선순위가 있는 문자열 후보 선택
	private static String firstNonBlank(String first, String second, String fallback) {
		if (first != null && !first.isBlank()) {
			// 첫 번째 후보 우선 사용
			return first;
		}

		if (second != null && !second.isBlank()) {
			// 두 번째 후보 사용
			return second;
		}

		// 모든 후보가 없을 때 기본값 사용
		return fallback;
	}

	// Kakao 계정용 가상 이메일 생성
	private static String createKakaoVirtualEmail(String providerUserId) {
		// 제공자 사용자 ID 기반 ASCII 가상 이메일 조립
		return "kakao_" + providerUserId + "@kakao.com";
	}

	// OAuth2 인증 실패로 전달할 제공자 오류 생성
	private static OAuth2AuthenticationException providerException(String message, Throwable cause) {
		// 실패 핸들러에서 허용하는 공통 오류 코드 생성
		OAuth2Error error = new OAuth2Error(PROVIDER_ERROR);

		// 원인 예외 존재 여부에 따른 OAuth2 예외 생성
		return cause == null
			? new OAuth2AuthenticationException(error, message)
			: new OAuth2AuthenticationException(error, message, cause);
	}
}
