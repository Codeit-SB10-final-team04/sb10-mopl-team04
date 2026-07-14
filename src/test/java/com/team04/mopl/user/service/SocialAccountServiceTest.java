package com.team04.mopl.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;

import com.team04.mopl.auth.security.oauth2.OAuth2UserInfo;
import com.team04.mopl.user.entity.EmailType;
import com.team04.mopl.user.entity.SocialAccount;
import com.team04.mopl.user.entity.SocialProvider;
import com.team04.mopl.user.entity.User;
import com.team04.mopl.user.repository.SocialAccountRepository;
import com.team04.mopl.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class SocialAccountServiceTest {

	@Mock
	private SocialAccountRepository socialAccountRepository;

	@Mock
	private UserRepository userRepository;

	@Mock
	private SocialAccount socialAccount;

	@Mock
	private User user;

	@Mock
	private User savedUser;

	@InjectMocks
	private SocialAccountService socialAccountService;

	@Test
	@DisplayName("이미 연결된 소셜 계정이면 연결된 사용자를 반환한다")
	void loginOrCreate_returnLinkedUser_whenSocialAccountExists() {
		// given
		OAuth2UserInfo userInfo = googleUserInfo();
		given(socialAccountRepository.findByProviderAndProviderUserId(
			SocialProvider.GOOGLE,
			"google-user-id"
		)).willReturn(Optional.of(socialAccount));
		given(socialAccount.getUser()).willReturn(user);
		given(user.isLocked()).willReturn(false);

		// when
		User result = socialAccountService.loginOrCreate(userInfo);

		// then
		assertThat(result).isSameAs(user);
		verify(userRepository, never()).saveAndFlush(any(User.class));
	}

	@Test
	@DisplayName("처음 로그인한 소셜 계정이면 사용자와 연결 정보를 생성한다")
	void loginOrCreate_createUserAndSocialAccount_whenSocialAccountDoesNotExist() {
		// given
		OAuth2UserInfo userInfo = googleUserInfo();
		given(socialAccountRepository.findByProviderAndProviderUserId(
			SocialProvider.GOOGLE,
			"google-user-id"
		)).willReturn(Optional.empty());
		given(userRepository.existsByEmail(userInfo.email())).willReturn(false);
		given(userRepository.saveAndFlush(any(User.class))).willReturn(savedUser);

		ArgumentCaptor<SocialAccount> socialAccountCaptor = ArgumentCaptor.forClass(SocialAccount.class);

		// when
		User result = socialAccountService.loginOrCreate(userInfo);

		// then
		assertThat(result).isSameAs(savedUser);
		verify(socialAccountRepository).saveAndFlush(socialAccountCaptor.capture());
		assertThat(socialAccountCaptor.getValue().getUser()).isSameAs(savedUser);
		assertThat(socialAccountCaptor.getValue().getProvider()).isEqualTo(SocialProvider.GOOGLE);
		assertThat(socialAccountCaptor.getValue().getProviderUserId()).isEqualTo("google-user-id");
		assertThat(socialAccountCaptor.getValue().getProviderEmail()).isEqualTo("user@gmail.com");
	}

	@Test
	@DisplayName("연결된 사용자가 잠겨 있으면 소셜 로그인을 거부한다")
	void loginOrCreate_throwAccountLocked_whenLinkedUserIsLocked() {
		// given
		OAuth2UserInfo userInfo = googleUserInfo();
		given(socialAccountRepository.findByProviderAndProviderUserId(
			SocialProvider.GOOGLE,
			"google-user-id"
		)).willReturn(Optional.of(socialAccount));
		given(socialAccount.getUser()).willReturn(user);
		given(user.isLocked()).willReturn(true);

		// when & then
		assertThatThrownBy(() -> socialAccountService.loginOrCreate(userInfo))
			.isInstanceOfSatisfying(OAuth2AuthenticationException.class, exception ->
				assertThat(exception.getError().getErrorCode()).isEqualTo("account_locked")
			);
		verify(userRepository, never()).existsByEmail(any(String.class));
	}

	@Test
	@DisplayName("동일 이메일 사용자가 이미 있으면 소셜 로그인을 거부한다")
	void loginOrCreate_throwEmailAlreadyExists_whenEmailAlreadyInUse() {
		// given
		OAuth2UserInfo userInfo = googleUserInfo();
		given(socialAccountRepository.findByProviderAndProviderUserId(
			SocialProvider.GOOGLE,
			"google-user-id"
		)).willReturn(Optional.empty());
		given(userRepository.existsByEmail(userInfo.email())).willReturn(true);

		// when & then
		assertThatThrownBy(() -> socialAccountService.loginOrCreate(userInfo))
			.isInstanceOfSatisfying(OAuth2AuthenticationException.class, exception ->
				assertThat(exception.getError().getErrorCode()).isEqualTo("email_already_exists")
			);
		verify(userRepository, never()).saveAndFlush(any(User.class));
		verify(socialAccountRepository, never()).saveAndFlush(any(SocialAccount.class));
	}

	@Test
	@DisplayName("사용자 저장 중 이메일 유니크 제약이 충돌하면 소셜 로그인을 거부한다")
	void loginOrCreate_throwEmailAlreadyExists_whenUserSaveConflictsByEmail() {
		// given
		OAuth2UserInfo userInfo = googleUserInfo();
		given(socialAccountRepository.findByProviderAndProviderUserId(
			SocialProvider.GOOGLE,
			"google-user-id"
		)).willReturn(Optional.empty());
		given(userRepository.existsByEmail(userInfo.email())).willReturn(false);
		given(userRepository.saveAndFlush(any(User.class)))
			.willThrow(new DataIntegrityViolationException("duplicate email"));

		// when & then
		assertThatThrownBy(() -> socialAccountService.loginOrCreate(userInfo))
			.isInstanceOfSatisfying(OAuth2AuthenticationException.class, exception ->
				assertThat(exception.getError().getErrorCode()).isEqualTo("email_already_exists")
			);
		verify(socialAccountRepository, never()).saveAndFlush(any(SocialAccount.class));
	}

	private OAuth2UserInfo googleUserInfo() {
		return new OAuth2UserInfo(
			SocialProvider.GOOGLE,
			"google-user-id",
			"user@gmail.com",
			EmailType.REAL,
			"Google 사용자",
			null,
			"user@gmail.com"
		);
	}
}
