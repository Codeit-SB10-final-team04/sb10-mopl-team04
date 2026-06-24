package com.team04.mopl.content.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.team04.mopl.content.entity.Tag;

public interface TagRepository extends JpaRepository<Tag, UUID> {
	// 태그명으로 조회
	Optional<Tag> findByName(String name);
}
