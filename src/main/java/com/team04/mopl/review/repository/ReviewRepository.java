package com.team04.mopl.review.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.team04.mopl.review.entity.Review;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

	boolean existsByUserIdAndContentIdAndDeletedAtIsNull(UUID userId, UUID contentId);
}
