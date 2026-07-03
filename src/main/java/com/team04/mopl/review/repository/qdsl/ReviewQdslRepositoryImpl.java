package com.team04.mopl.review.repository.qdsl;

import static com.team04.mopl.review.entity.QReview.*;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.team04.mopl.common.enums.SortDirection;
import com.team04.mopl.common.exception.MoplException;
import com.team04.mopl.review.dto.request.ReviewPageRequest;
import com.team04.mopl.review.dto.response.ReviewCursorPage;
import com.team04.mopl.review.entity.Review;
import com.team04.mopl.review.enums.ReviewSortBy;
import com.team04.mopl.review.exception.ReviewErrorCode;
import com.team04.mopl.review.exception.ReviewException;

@Repository
public class ReviewQdslRepositoryImpl implements ReviewQdslRepository {

	private final JPAQueryFactory jpaQueryFactory;

	public ReviewQdslRepositoryImpl(JPAQueryFactory jpaQueryFactory) {
		this.jpaQueryFactory = jpaQueryFactory;
	}

	@Override
	public ReviewCursorPage getReviews(ReviewPageRequest request) {
		String cursor = request.cursor();
		ReviewSortBy sortBy = request.sortBy();
		SortDirection sortDirection = request.sortDirection();
		UUID idAfter = request.idAfter();
		UUID contentId = request.contentId();
		int limit = request.limit();

		// 1. 조건에 맞는 리뷰 조회 (다음 페이지 존재 여부 판단을 위해 limit + 1)
		List<Review> rows = jpaQueryFactory
			.select(review)
			.from(review)
			.where(
				review.content.id.eq(contentId),
				review.deletedAt.isNull(),
				cursorCondition(cursor, sortBy, sortDirection, idAfter)
			)
			.orderBy(orderCondition(sortDirection, sortBy))
			.limit(limit + 1)
			.fetch();

		// 2. 다음 페이지 여부 판단 후 실제 반환할 데이터만 자르기
		boolean hasNext = rows.size() > limit;
		List<Review> reviewList = hasNext
			? rows.subList(0, limit)
			: rows;

		// 3. 전체 데이터 개수 조회
		Long totalCount = getTotalCount(contentId);

		return new ReviewCursorPage(reviewList, hasNext, totalCount);
	}

	// sortBy에 따라 커서 조건 생성 (createdAt 또는 rating)
	private BooleanExpression cursorCondition(
		String cursor,
		ReviewSortBy sortBy,
		SortDirection sortDirection,
		UUID idAfter
	) {
		if (cursor == null) {
			return null;
		}

		if (sortBy.equals(ReviewSortBy.createdAt)) {
			Instant cursorCreatedAt = parseStringToInstant(cursor);
			return createdAtCursor(sortDirection, idAfter, cursorCreatedAt);
		}

		if (sortBy.equals(ReviewSortBy.rating)) {
			short cursorRating = parseStringToShort(cursor);
			return ratingCursor(sortDirection, idAfter, cursorRating);
		}

		throw new ReviewException(ReviewErrorCode.REVIEW_INVALID_INPUT)
			.addDetail("sortBy", sortBy)
			.addDetail("message", "적합하지 않은 sortBy입니다.");
	}

	// createdAt 기준 커서 조건 (DESCENDING: 이전 값보다 작은 것, ASCENDING: 이전 값보다 큰 것)
	private BooleanExpression createdAtCursor(
		SortDirection sortDirection,
		UUID idAfter,
		Instant cursorCreatedAt
	) {
		if (sortDirection.equals(SortDirection.DESCENDING)) {
			return review.createdAt.lt(cursorCreatedAt)
				.or(
					review.createdAt.eq(cursorCreatedAt)
						.and(review.id.lt(idAfter))
				);
		}

		if (sortDirection.equals(SortDirection.ASCENDING)) {
			return review.createdAt.gt(cursorCreatedAt)
				.or(
					review.createdAt.eq(cursorCreatedAt)
						.and(review.id.gt(idAfter))
				);
		}

		throw invalidSortDirectionException(sortDirection);
	}

	// rating 기준 커서 조건
	private BooleanExpression ratingCursor(
		SortDirection sortDirection,
		UUID idAfter,
		short cursorRating
	) {
		if (sortDirection.equals(SortDirection.DESCENDING)) {
			return review.rating.lt(cursorRating)
				.or(
					review.rating.eq(cursorRating)
						.and(review.id.lt(idAfter))
				);
		}

		if (sortDirection.equals(SortDirection.ASCENDING)) {
			return review.rating.gt(cursorRating)
				.or(
					review.rating.eq(cursorRating)
						.and(review.id.gt(idAfter))
				);
		}

		throw invalidSortDirectionException(sortDirection);
	}

	// sortBy에 따라 정렬 조건 생성
	private OrderSpecifier<?>[] orderCondition(
		SortDirection sortDirection,
		ReviewSortBy sortBy
	) {
		if (sortBy.equals(ReviewSortBy.createdAt)) {
			return createdAtOrder(sortDirection);
		}

		if (sortBy.equals(ReviewSortBy.rating)) {
			return ratingOrder(sortDirection);
		}

		throw new ReviewException(ReviewErrorCode.REVIEW_INVALID_INPUT)
			.addDetail("sortBy", sortBy)
			.addDetail("message", "적합하지 않은 sortBy입니다.");
	}

	// createdAt 정렬 (보조 정렬: id)
	private OrderSpecifier<?>[] createdAtOrder(SortDirection sortDirection) {
		boolean descending = isDescending(sortDirection);
		return new OrderSpecifier<?>[] {
			descending ? review.createdAt.desc() : review.createdAt.asc(),
			descending ? review.id.desc() : review.id.asc()
		};
	}

	// rating 정렬 (보조 정렬: id)
	private OrderSpecifier<?>[] ratingOrder(SortDirection sortDirection) {
		boolean descending = isDescending(sortDirection);
		return new OrderSpecifier<?>[] {
			descending ? review.rating.desc() : review.rating.asc(),
			descending ? review.id.desc() : review.id.asc()
		};
	}

	private boolean isDescending(SortDirection sortDirection) {
		return sortDirection.equals(SortDirection.DESCENDING);
	}

	// 해당 콘텐츠의 삭제되지 않은 리뷰 전체 개수 조회
	private Long getTotalCount(UUID contentId) {
		return jpaQueryFactory
			.select(review.count())
			.from(review)
			.where(
				review.content.id.eq(contentId),
				review.deletedAt.isNull()
			)
			.fetchOne();
	}

	// String → Instant 파싱
	private Instant parseStringToInstant(String cursor) {
		try {
			return Instant.parse(cursor);
		} catch (DateTimeParseException e) {
			throw invalidCursorTypeException(cursor, e);
		}
	}

	// String → short 파싱
	private short parseStringToShort(String cursor) {
		try {
			return Short.parseShort(cursor);
		} catch (NumberFormatException e) {
			throw invalidCursorTypeException(cursor, e);
		}
	}

	private MoplException invalidCursorTypeException(Object cursor, Throwable e) {
		return new ReviewException(ReviewErrorCode.REVIEW_INVALID_INPUT, e)
			.addDetail("cursor", cursor)
			.addDetail("message", "적합하지 않은 cursor 타입입니다.");
	}

	private MoplException invalidSortDirectionException(SortDirection sortDirection) {
		return new ReviewException(ReviewErrorCode.REVIEW_INVALID_INPUT)
			.addDetail("sortDirection", sortDirection)
			.addDetail("message", "적합하지 않은 sortDirection입니다.");
	}
}
