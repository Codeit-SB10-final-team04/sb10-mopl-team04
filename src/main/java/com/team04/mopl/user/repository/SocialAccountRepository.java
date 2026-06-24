package com.team04.mopl.user.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.team04.mopl.user.entity.SocialAccount;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, UUID> {
}
