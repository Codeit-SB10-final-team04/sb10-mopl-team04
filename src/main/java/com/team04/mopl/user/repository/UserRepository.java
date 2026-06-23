package com.team04.mopl.user.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.team04.mopl.user.entity.User;

public interface UserRepository extends JpaRepository<User, UUID> {
}
