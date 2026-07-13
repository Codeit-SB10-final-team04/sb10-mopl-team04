package com.team04.mopl.conversation.repository.qdsl;

import static com.team04.mopl.conversation.entity.QConversation.*;

import java.util.List;
import java.util.UUID;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.team04.mopl.conversation.entity.Conversation;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class ConversationQdslRepositoryImpl implements ConversationQdslRepository {

	private final JPAQueryFactory queryFactory;

	// 필터링 + 정렬 + 커서 기반 페이지네이션이 적용된 대화 목록 조회
	@Override
	public List<Conversation> findByIdIn(List<UUID> ids) {
		// [CQRS] ES에서 넘겨준 ID 목록으로 RDB에서 상세 데이터 추출
		return queryFactory
			.selectFrom(conversation)
			.where(conversation.id.in(ids))
			.fetch();
	}
}
