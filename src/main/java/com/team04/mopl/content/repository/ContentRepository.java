package com.team04.mopl.content.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.team04.mopl.content.entity.CollectionSource;
import com.team04.mopl.content.entity.Content;
import com.team04.mopl.content.repository.qdsl.ContentQdslRepository;

public interface ContentRepository extends JpaRepository<Content, UUID>, ContentQdslRepository {

	/**
	 * externalId + source 조합으로 중복 수집 여부 확인
	 */
	boolean existsByExternalIdAndSource(String externalId, CollectionSource source);

	Optional<Content> findByIdAndDeletedAtIsNull(UUID contentId);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query(
		"UPDATE Content c SET "
			+ "c.averageRating = COALESCE("
			+ "(SELECT AVG(r.rating) FROM Review r "
			+ "WHERE r.content.id = c.id AND r.deletedAt IS NULL), 0.0), "
			+ "c.reviewCount = "
			+ "(SELECT COUNT(r) FROM Review r "
			+ "WHERE r.content.id = c.id AND r.deletedAt IS NULL) "
			+ "WHERE c.id = :contentId AND c.deletedAt IS NULL")
	int refreshRatingAggregate(@Param("contentId") UUID contentId);
}
