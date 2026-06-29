package com.team04.mopl.content.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.team04.mopl.content.entity.CollectionSource;
import com.team04.mopl.content.entity.Content;
import com.team04.mopl.content.repository.qdsl.ContentQdslRepository;

public interface ContentRepository extends JpaRepository<Content, UUID>, ContentQdslRepository {

	/**
	 * externalId + source 조합으로 중복 수집 여부 확인
	 */
	boolean existsByExternalIdAndSource(String externalId, CollectionSource source);

	Optional<Content> findByIdAndDeletedAtIsNull(UUID contentId);
}
