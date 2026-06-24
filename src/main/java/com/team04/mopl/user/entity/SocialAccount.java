package com.team04.mopl.user.entity;

import com.team04.mopl.common.entity.BaseEntity;
import com.team04.mopl.user.exception.UserErrorCode;
import com.team04.mopl.user.exception.UserException;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
	name = "social_accounts",
	uniqueConstraints = {
		@UniqueConstraint(
			name = "uq_social_accounts_provider",
			columnNames = {"social_provider", "provider_user_id"}
		)
	}
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SocialAccount extends BaseEntity {

	// 소셜 계정이 연결된 서비스 사용자
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(
		name = "user_id",
		nullable = false,
		columnDefinition = "UUID"
	)
	private User user;

	// Google 또는 Kakao
	@Enumerated(EnumType.STRING)
	@Column(name = "social_provider", nullable = false, length = 20)
	private SocialProvider provider;

	// Google/Kakao에서 제공하는 사용자 고유 식별자
	@Column(name = "provider_user_id", nullable = false, length = 255)
	private String providerUserId;

	// 제공자가 실제로 전달한 이메일
	// Kakao가 이메일을 제공하지 않으면 null
	@Column(name = "provider_email", length = 255)
	private String providerEmail;

	@Builder
	protected SocialAccount(
		User user,
		SocialProvider provider,
		String providerUserId,
		String providerEmail
	) {
		validateUser(user);
		validateProvider(provider);
		validateProviderUserId(providerUserId);

		this.user = user;
		this.provider = provider;
		this.providerUserId = providerUserId;
		this.providerEmail = providerEmail;
	}

	private static void validateUser(User user) {
		if (user == null) {
			throw new UserException(UserErrorCode.SOCIAL_ACCOUNT_USER_REQUIRED);
		}
	}

	private static void validateProvider(SocialProvider provider) {
		if (provider == null) {
			throw new UserException(UserErrorCode.SOCIAL_PROVIDER_REQUIRED);
		}
	}

	private static void validateProviderUserId(String providerUserId) {
		if (providerUserId == null || providerUserId.isBlank()) {
			throw new UserException(UserErrorCode.SOCIAL_PROVIDER_USER_ID_REQUIRED);
		}
	}
}
