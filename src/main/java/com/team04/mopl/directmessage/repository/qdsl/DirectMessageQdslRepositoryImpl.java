package com.team04.mopl.directmessage.repository.qdsl;

import static com.team04.mopl.directmessage.entity.QDirectMessage.*;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.util.StringUtils;

import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.team04.mopl.common.enums.SortDirection;
import com.team04.mopl.directmessage.dto.request.DirectMessagePagedRequest;
import com.team04.mopl.directmessage.entity.DirectMessage;
import com.team04.mopl.directmessage.exception.DirectMessageErrorCode;
import com.team04.mopl.directmessage.exception.DirectMessageException;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class DirectMessageQdslRepositoryImpl implements DirectMessageQdslRepository {

	private final JPAQueryFactory jpaQueryFactory;

	// 정렬 + 커서 페이지네이션이 적용된 DM 목록 조회
	@Override
	public List<DirectMessage> findDirectMessageByCursor(
		UUID conversationId,
		DirectMessagePagedRequest directMessagePagedRequest,
		UUID requestId
	) {
		return jpaQueryFactory
			.selectFrom(directMessage)
			.where(
				// 1. 유효성 검증: 해당 대화방(conversationId)의 메시지만 조회
				directMessage.conversation.id.eq(conversationId),
				// 2. 커서 페이지네이션 적용: 마지막 메시지의 생성 시간 및 ID 값
				cursorCondition(
					directMessagePagedRequest.cursor(),
					directMessagePagedRequest.idAfter(),
					directMessagePagedRequest.sortDirection()
				)
			)
			// 3. 정렬: 생성 시간 및 ID
			.orderBy(createOrderSpecifier(directMessagePagedRequest))
			.limit(directMessagePagedRequest.limit() + 1)
			.fetch();
	}

	// DM 목록의 전체 개수 조회
	@Override
	public Long countDirectMessage(
		UUID conversationId,
		DirectMessagePagedRequest directMessagePagedRequest,
		UUID requestId
	) {
		Long count = jpaQueryFactory
			.select(directMessage.count())
			.from(directMessage)
			.where(
				// 특정 대화방의 메시지 개수 카운트
				directMessage.conversation.id.eq(conversationId)
			)
			.fetchOne();

		// NPE 방지를 위해 기본값으로 0 반환
		return Optional.ofNullable(count).orElse(0L);
	}

	// 커서 페이지네이션
	private BooleanExpression cursorCondition(
		String cursor,
		UUID idAfter,
		SortDirection sortDirection
	) {
		// 첫 페이지 요청이거나 커서값이 공백인 경우, 빈 객체 반환
		if (!StringUtils.hasText(cursor) || idAfter == null) {
			return null;
		}

		// 마지막 요소의 커서값 (생성 시간)
		Instant cursorTime = parseCursorToInstant(cursor);

		if (sortDirection == SortDirection.DESCENDING) {
			// 내림차순 정렬: 마지막 요소보다 생성 시간이 과거이거나, 생성 시간은 같고 ID 값이 작은 DM
			return directMessage.createdAt.lt(cursorTime)
				.or(directMessage.createdAt.eq(cursorTime).and(directMessage.id.lt(idAfter)));
		} else {
			// 오름차순 정렬: 마지막 요소보다 생성 시간이 미래이거나, 생성 시간은 같고 ID 값이 큰 DM
			return directMessage.createdAt.gt(cursorTime)
				.or(directMessage.createdAt.eq(cursorTime).and(directMessage.id.gt(idAfter)));
		}
	}

	// 커서 반환 (문자열 -> Instant)
	private Instant parseCursorToInstant(String cursor) {
		try {
			return Instant.parse(cursor);
		} catch (DateTimeParseException e) {
			// 커서 값이 잘못된 형태인 경우, 예외 발생
			throw new DirectMessageException(DirectMessageErrorCode.DM_INVALID_FORMAT, e)
				.addDetail("invalidCursor", cursor);
		}
	}

	// 정렬
	private OrderSpecifier<?>[] createOrderSpecifier(DirectMessagePagedRequest request) {
		// 유효성 검증: 정렬 기준 일치 여부
		validateSortBy(request.sortBy());

		// 기본값 (내림차순)
		Order direction = request.sortDirection() == SortDirection.DESCENDING
			? Order.DESC
			: Order.ASC;

		return new OrderSpecifier[] {
			// 1순위 정렬: 생성 시간 (createdAt)
			new OrderSpecifier<>(direction, directMessage.createdAt),
			// 2순위 정렬: 생성 시간이 똑같을 경우, ID 기준 정렬
			new OrderSpecifier<>(direction, directMessage.id)
		};
	}

	// 유효성 검증: 정렬 기준 일치 여부
	private void validateSortBy(String sortBy) {
		if (!"createdAt".equals(sortBy)) {
			throw new DirectMessageException(DirectMessageErrorCode.DM_INVALID_FORMAT)
				.addDetail("sortBy", sortBy);
		}
	}
}
