package com.team04.mopl.notification.repository.qdsl;

import static com.team04.mopl.notification.entity.QNotification.*;

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
import com.team04.mopl.notification.dto.request.NotificationPageRequest;
import com.team04.mopl.notification.dto.response.NotificationCursorPage;
import com.team04.mopl.notification.entity.Notification;
import com.team04.mopl.notification.enums.NotificationSortBy;
import com.team04.mopl.notification.exception.NotificationErrorCode;
import com.team04.mopl.notification.exception.NotificationException;

@Repository
public class NotificationQdslRepositoryImpl implements NotificationQdslRepository {

	private final JPAQueryFactory jpaQueryFactory;

	public NotificationQdslRepositoryImpl(JPAQueryFactory jpaQueryFactory) {
		this.jpaQueryFactory = jpaQueryFactory;
	}

	@Override
	public NotificationCursorPage findAllNotifications(
		NotificationPageRequest request,
		UUID currentUserId
	) {
		String cursor = request.cursor();
		NotificationSortBy sortBy = request.sortBy();
		SortDirection sortDirection = request.sortDirection();
		UUID idAfter = request.idAfter();
		int limit = request.limit();

		List<Notification> rows = jpaQueryFactory
			.select(notification)
			.from(notification)
			.where(
				// 읽지 않은 알림 (notifications.read_at IS NULL)
				notification.readAt.isNull(),
				// 현재 로그인한 사용자가 receiver인 경우
				notification.receiver.id.eq(currentUserId),
				// createdAt 커서 조건
				createdAtCursorCondition(
					cursor,
					sortBy,
					sortDirection,
					idAfter
				)
			)
			// 커서 조건에 따라 정렬
			.orderBy(createdAtOrderCondition(
					sortDirection,
					sortBy
				)
			)
			// 최대 수집 횟수 (다음 데이터 존재 여부를 위해 +1)
			.limit(limit + 1)
			.fetch();

		// 다음 페이지 여부
		boolean hasNext = rows.size() > limit;
		List<Notification> notificationList = hasNext
			? rows.subList(0, limit)
			: rows;

		// 조건에 따른 전체 데이터 개수 조회
		Long totalCount = getTotalCount(currentUserId);

		return new NotificationCursorPage(notificationList, hasNext, totalCount);
	}

	private BooleanExpression createdAtCursorCondition(
		String cursor,
		NotificationSortBy sortBy,
		SortDirection sortDirection,
		UUID idAfter
	) {
		if (cursor == null) {
			return null;
		}

		if (sortBy.equals(NotificationSortBy.createdAt)) {
			Instant cursorCreatedAt = parserStringToInstant(cursor);
			return createdAtCursor(
				sortDirection,
				idAfter,
				cursorCreatedAt
			);
		}

		throw new NotificationException(NotificationErrorCode.NOTIFICATION_INVALID_INPUT)
			.addDetail("sortBy", sortBy)
			.addDetail("message", "적합하지 않은 sortBy 입니다.");
	}

	// String 타입의 cursor -> Instant 타입으로 parse
	private Instant parserStringToInstant(String cursor) {
		try {
			return Instant.parse(cursor);
		} catch (DateTimeParseException e) {
			// cursor가 Instant 타입이 아닐 경우 예외 발생
			throw invalidCursorTypeException(cursor, e);
		}
	}

	private BooleanExpression createdAtCursor(
		SortDirection sortDirection,
		UUID idAfter,
		Instant cursorCreatedAt
	) {
		if (sortDirection.equals(SortDirection.DESCENDING)) {
			return notification.createdAt.lt(cursorCreatedAt)
				.or(
					notification.createdAt.eq(cursorCreatedAt)
						.and(notification.id.lt(idAfter))
				);
		}

		if (sortDirection.equals(SortDirection.ASCENDING)) {
			return notification.createdAt.gt(cursorCreatedAt)
				.or(
					notification.createdAt.eq(cursorCreatedAt)
						.and(notification.id.gt(idAfter))
				);
		}

		// 정렬 방향에 DESCENDING이나 ASCENDING 이외의 것이 입력되었을 경우 예외 발생
		throw invalidSortDirectionException(sortDirection);
	}

	private OrderSpecifier<?>[] createdAtOrderCondition(
		SortDirection sortDirection,
		NotificationSortBy sortBy
	) {
		if (sortBy.equals(NotificationSortBy.createdAt)) {
			return createdAtOrder(sortDirection);
		}

		// 정렬 조건에 createdAt 이외의 것이 입력되었을 경우 예외 발생
		throw invalidSortByException(sortBy);
	}

	private OrderSpecifier<?>[] createdAtOrder(SortDirection sortDirection) {
		boolean descending = isDescending(sortDirection);

		return new OrderSpecifier<?>[] {
			descending
				? notification.createdAt.desc()
				: notification.createdAt.asc(),
			descending
				? notification.id.desc()
				: notification.id.asc()
		};
	}

	private boolean isDescending(SortDirection sortDirection) {
		return sortDirection.equals(SortDirection.DESCENDING);
	}

	// 조건에 따른 전체 데이터 개수 조회
	private Long getTotalCount(UUID currentUserId) {
		return jpaQueryFactory
			.select(notification.count())
			.from(notification)
			.where(
				// 읽지 않은 알림 (notifications.read_at IS NULL)
				notification.readAt.isNull(),
				// 현재 로그인한 사용자가 receiver인 경우
				notification.receiver.id.eq(currentUserId)
			)
			.fetchOne();
	}

	// validate
	// cursor가 Instant 타입이 아닐 경우 예외 발생
	private MoplException invalidCursorTypeException(Object cursor, Throwable e) {
		return new NotificationException(NotificationErrorCode.NOTIFICATION_INVALID_INPUT, e)
			.addDetail("cursor", cursor)
			.addDetail("message", "적합하지 않은 cursor 타입입니다.");
	}

	// 정렬 방향에 DESCENDING이나 ASCENDING 이외의 것이 입력되었을 경우 예외 발생
	private MoplException invalidSortDirectionException(SortDirection sortDirection) {
		return new NotificationException(NotificationErrorCode.NOTIFICATION_INVALID_INPUT)
			.addDetail("sortDirection", sortDirection)
			.addDetail("message", "적합하지 않은 sortDirection입니다.");
	}

	// 정렬 조건에 createdAt 이외의 것이 입력되었을 경우 예외 발생
	private MoplException invalidSortByException(NotificationSortBy sortBy) {
		return new NotificationException(NotificationErrorCode.NOTIFICATION_INVALID_INPUT)
			.addDetail("sortBy", sortBy)
			.addDetail("message", "적합하지 않은 sortBy입니다.");
	}
}
