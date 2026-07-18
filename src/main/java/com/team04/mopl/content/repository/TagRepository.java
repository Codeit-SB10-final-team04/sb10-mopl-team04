package com.team04.mopl.content.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.team04.mopl.content.entity.Tag;

public interface TagRepository extends JpaRepository<Tag, UUID> {
	// 태그명으로 조회
	Optional<Tag> findByName(String name);

	// 태그명 목록으로 일괄 조회
	List<Tag> findAllByNameIn(List<String> names);

	// 태그 저장 (중복 시 무시) - 동시성 안전
	@Modifying
	@Query(value = "INSERT INTO tags (id, created_at, name) VALUES (:id, NOW(), :name) ON CONFLICT (name) DO NOTHING",
		nativeQuery = true)
	void insertIgnore(@Param("id") UUID id, @Param("name") String name);
}
