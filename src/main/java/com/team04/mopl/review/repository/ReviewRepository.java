package com.team04.mopl.review.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.team04.mopl.review.entity.Review;
import com.team04.mopl.review.repository.qdsl.ReviewQdslRepository;

public interface ReviewRepository extends JpaRepository<Review, UUID>, ReviewQdslRepository {

	@Query(value = """
		SELECT EXISTS(
		    SELECT 1 FROM content_reviews
		    WHERE user_id = :userId
		      AND content_id = :contentId
		      AND deleted_at IS NULL
		)
		""", nativeQuery = true)
	boolean existsByUserIdAndContentIdAndDeletedAtIsNull(@Param("userId") UUID userId,
		@Param("contentId") UUID contentId);

	Optional<Review> findByIdAndDeletedAtIsNull(UUID id);

	List<Review> findAllByContentIdAndDeletedAtIsNull(UUID contentId);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("UPDATE Review r SET r.deletedAt = :now WHERE r.content.id = :contentId AND r.deletedAt IS NULL")
	int bulkMarkDeletedByContentId(@Param("contentId") UUID contentId, @Param("now") Instant now);
}
