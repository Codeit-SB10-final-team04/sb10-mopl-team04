package com.team04.mopl.auth.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.team04.mopl.auth.entity.AuthSession;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {
}
