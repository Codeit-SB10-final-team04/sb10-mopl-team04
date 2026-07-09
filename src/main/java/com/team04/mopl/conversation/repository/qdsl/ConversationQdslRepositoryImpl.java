package com.team04.mopl.conversation.repository.qdsl;

import static com.team04.mopl.conversation.entity.QConversation.*;
import static com.team04.mopl.conversation.entity.QConversationParticipant.*;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.util.StringUtils;

import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.team04.mopl.common.enums.SortDirection;
import com.team04.mopl.conversation.dto.request.ConversationPageRequest;
import com.team04.mopl.conversation.entity.Conversation;
import com.team04.mopl.conversation.entity.QConversationParticipant;
import com.team04.mopl.conversation.exception.ConversationErrorCode;
import com.team04.mopl.conversation.exception.ConversationException;
import com.team04.mopl.directmessage.entity.QDirectMessage;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ConversationQdslRepositoryImpl implements ConversationQdslRepository {

	private final JPAQueryFactory queryFactory;

	// 필터링 + 정렬 + 커서 기반 페이지네이션이 적용된 대화 목록 조회
	@Override
	public List<Conversation> searchConversation(
		ConversationPageRequest request,
		UUID requestUserId
	) {
		return queryFactory
			.select(conversation)
			.from(conversationParticipant)
			// 조인: 대화 참여자 (Conversation Participant) 및 대화 (Conversation)
			.join(conversationParticipant.conversation, conversation)
			.where(
				// 1. 유효성 검증: 요청자가 대화 참여자인지 검증
				conversationParticipant.user.id.eq(requestUserId),
				// 2. 필터링: 대화 상대 닉네임 or 메시지 내용
				containsKeyword(request.keywordLike(), requestUserId),
				// 3. 커서 페이지네이션 적용: 마지막 메시지 내용의 생성 시간 및 ID 값
				cursorCondition(request.cursor(), request.idAfter(), request.sortDirection())
			)
			// 정렬: 생성 시간
			.orderBy(createOrderSpecifier(request))
			.limit(request.limit() + 1)
			.fetch();
	}

	// 필터링이 조건이 적용된 대화 목록의 전체 개수 조회
	@Override
	public Long countConversation(
		ConversationPageRequest request,
		UUID requestUserId
	) {
		Long count = queryFactory
			.select(conversation.count())
			.from(conversationParticipant)
			.join(conversationParticipant.conversation, conversation)
			.where(
				// 1. 유효성 검증: 요청자가 대화 참여자인지 검증
				conversationParticipant.user.id.eq(requestUserId),
				// 2. 필터링: 대화 상대 닉네임 or 메시지 내용
				containsKeyword(request.keywordLike(), requestUserId)
			)
			.fetchOne();

		// NPE 방지를 위해 기본값으로 0 반환
		return Optional.ofNullable(count).orElse(0L);
	}

	// 필터링: 대화 상대 닉네임 or 메시지 내용 내 검색어 (keyword) 포함 여부
	private BooleanExpression containsKeyword(
		String keyword,
		UUID requestUserId
	) {
		// 키워드가 공백인 경우, 빈 객체 반환
		if (!StringUtils.hasText(keyword)) {
			return null;
		}

		// 서브 쿼리를 위한 가짜 테이블 객체
		QConversationParticipant cpSub = new QConversationParticipant("cpSub");        // 대화 참여자
		QDirectMessage dmSub = new QDirectMessage("dmSub");                            // 메시지 내용

		// 대화 상대 닉네임 검색
		BooleanExpression matchOpponentName = JPAExpressions.selectOne()
			.from(cpSub)
			.where(
				// 유효성 검증: 메인 쿼리와 서브 쿼리 간의 대화방 일치 여부
				cpSub.conversation.eq(conversation),
				// 요청자 필터링: 대화 상대 정보 내에서 검색
				cpSub.user.id.ne(requestUserId),
				// 닉네임 필터링: 부분 일치 (대소문사 무시)
				cpSub.user.name.containsIgnoreCase(keyword)
			)
			.exists();

		// 메시지 내용 검색
		BooleanExpression matchMessageContent = JPAExpressions.selectOne()
			.from(dmSub)
			.where(
				// 유효성 검증: 메인 쿼리와 서브 쿼리 간의 대화방 일치 여부
				dmSub.conversation.eq(conversation),
				// 메시지 필터링: 부분 일치 (대소문자 무시)
				dmSub.content.containsIgnoreCase(keyword)
			)
			.exists();

		return matchOpponentName.or(matchMessageContent);
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
			// 내림차순 정렬: 마지막 요소보다 생성 시간이 과거이거나, 생성 시간은 같고 ID 값이 작은 대화
			return conversation.createdAt.lt(cursorTime)
				.or(conversation.createdAt.eq(cursorTime).and(conversation.id.lt(idAfter)));
		} else {
			// 오름차순 정렬: 마지막 요소보다 생성 시간이 미래이거나, 생성 시간은 같고 ID 값이 큰 대화
			return conversation.createdAt.gt(cursorTime)
				.or(conversation.createdAt.eq(cursorTime).and(conversation.id.gt(idAfter)));
		}
	}

	// 커서 반환
	private Instant parseCursorToInstant(String cursor) {
		try {
			return Instant.parse(cursor);
		} catch (DateTimeParseException e) {
			// 커서 값이 잘못된 형태인 경우, 예외 발생
			throw new ConversationException(ConversationErrorCode.CONVERSATION_INVALID_FORMAT, e)
				.addDetail("invalidCursor", cursor);
		}
	}

	// 정렬
	private OrderSpecifier<?>[] createOrderSpecifier(ConversationPageRequest request) {
		// 유효성 검증: 정렬 기준 일치 여부
		validateSortBy(request.sortBy());

		// 기본값 (내림차순)
		Order direction = request.sortDirection() == SortDirection.DESCENDING
			? Order.DESC
			: Order.ASC;

		return new OrderSpecifier[] {
			// 1순위 정렬: 생성 시간 (createdAt)
			new OrderSpecifier<>(direction, conversation.createdAt),
			// 2순위 정렬: 생성 시간이 똑같을 경우, ID 기준 정렬
			new OrderSpecifier<>(direction, conversation.id)
		};
	}

	// 유효성 검증: 정렬 기준 일치 여부
	private void validateSortBy(String sortBy) {
		if (!"createdAt".equals(sortBy)) {
			throw new ConversationException(ConversationErrorCode.CONVERSATION_INVALID_FORMAT)
				.addDetail("sortBy", sortBy);
		}
	}
}
