package com.team04.mopl.user.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.team04.mopl.user.entity.SocialAccount;
import com.team04.mopl.user.entity.SocialProvider;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, UUID> {

	Optional<SocialAccount> findByProviderAndProviderUserId(
		SocialProvider provider,
		String providerUserId
	);
}
