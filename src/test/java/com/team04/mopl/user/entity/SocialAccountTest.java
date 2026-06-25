package com.team04.mopl.user.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.team04.mopl.user.exception.UserErrorCode;
import com.team04.mopl.user.exception.UserException;

class SocialAccountTest {

	@Test
	@DisplayName("유효한 값으로 소셜 계정을 생성한다")
	void builder_createSocialAccount_whenValidInput() {
		// given
		User user = createUser();

		// when
		SocialAccount socialAccount = SocialAccount.builder()
			.user(user)
			.provider(SocialProvider.GOOGLE)
			.providerUserId("google-user-id")
			.providerEmail("google@test.com")
			.build();

		// then
		assertThat(socialAccount.getUser()).isEqualTo(user);
		assertThat(socialAccount.getProvider()).isEqualTo(SocialProvider.GOOGLE);
		assertThat(socialAccount.getProviderUserId()).isEqualTo("google-user-id");
		assertThat(socialAccount.getProviderEmail()).isEqualTo("google@test.com");
	}

	@Test
	@DisplayName("제공자 이메일이 null이어도 소셜 계정을 생성한다")
	void builder_createSocialAccount_whenProviderEmailIsNull() {
		// given
		User user = createUser();

		// when
		SocialAccount socialAccount = SocialAccount.builder()
			.user(user)
			.provider(SocialProvider.KAKAO)
			.providerUserId("kakao-user-id")
			.providerEmail(null)
			.build();

		// then
		assertThat(socialAccount.getUser()).isEqualTo(user);
		assertThat(socialAccount.getProvider()).isEqualTo(SocialProvider.KAKAO);
		assertThat(socialAccount.getProviderUserId()).isEqualTo("kakao-user-id");
		assertThat(socialAccount.getProviderEmail()).isNull();
	}

	@Test
	@DisplayName("사용자가 null이면 소셜 계정 생성에 실패한다")
	void builder_throwUserException_whenUserIsNull() {
		// given

		// when
		ThrowingCallable action = () -> SocialAccount.builder()
			.user(null)
			.provider(SocialProvider.GOOGLE)
			.providerUserId("google-user-id")
			.providerEmail("google@test.com")
			.build();

		// then
		assertUserException(action, UserErrorCode.SOCIAL_ACCOUNT_USER_REQUIRED);
	}

	@Test
	@DisplayName("소셜 제공자가 null이면 소셜 계정 생성에 실패한다")
	void builder_throwUserException_whenProviderIsNull() {
		// given
		User user = createUser();

		// when
		ThrowingCallable action = () -> SocialAccount.builder()
			.user(user)
			.provider(null)
			.providerUserId("provider-user-id")
			.providerEmail("provider@test.com")
			.build();

		// then
		assertUserException(action, UserErrorCode.SOCIAL_PROVIDER_REQUIRED);
	}

	@Test
	@DisplayName("소셜 사용자 ID가 null이면 소셜 계정 생성에 실패한다")
	void builder_throwUserException_whenProviderUserIdIsNull() {
		// given
		User user = createUser();

		// when
		ThrowingCallable action = () -> SocialAccount.builder()
			.user(user)
			.provider(SocialProvider.GOOGLE)
			.providerUserId(null)
			.providerEmail("google@test.com")
			.build();

		// then
		assertUserException(action, UserErrorCode.SOCIAL_PROVIDER_USER_ID_REQUIRED);
	}

	@Test
	@DisplayName("소셜 사용자 ID가 공백이면 소셜 계정 생성에 실패한다")
	void builder_throwUserException_whenProviderUserIdIsBlank() {
		// given
		User user = createUser();

		// when
		ThrowingCallable action = () -> SocialAccount.builder()
			.user(user)
			.provider(SocialProvider.GOOGLE)
			.providerUserId("   ")
			.providerEmail("google@test.com")
			.build();

		// then
		assertUserException(action, UserErrorCode.SOCIAL_PROVIDER_USER_ID_REQUIRED);
	}

	private static void assertUserException(
		ThrowingCallable action,
		UserErrorCode expectedErrorCode
	) {
		assertThatThrownBy(action)
			.isInstanceOfSatisfying(UserException.class, exception -> {
				assertThat(exception.getErrorCode()).isEqualTo(expectedErrorCode);
				assertThat(exception).hasMessage(expectedErrorCode.getMessage());
			});
	}

	private User createUser() {
		return User.builder()
			.name("사용자")
			.email("test@test.com")
			.passwordHash("encoded-password")
			.build();
	}
}