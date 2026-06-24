package com.team04.mopl.user.entity;

import java.util.Objects;

import com.team04.mopl.common.entity.BaseEntity;

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
		this.user = Objects.requireNonNull(user, "사용자는 필수입니다.");
		this.provider = Objects.requireNonNull(provider, "provider는 필수입니다.");
		this.providerUserId = requireText(providerUserId);
		this.providerEmail = providerEmail;
	}

	private static String requireText(String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException("소셜 사용자 ID는 필수입니다.");
		}
		return value;
	}
}
