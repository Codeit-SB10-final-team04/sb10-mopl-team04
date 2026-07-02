package com.team04.mopl.conversation.repository.qdsl;

import java.util.List;

import com.team04.mopl.conversation.dto.request.ConversationPageRequest;
import com.team04.mopl.conversation.entity.Conversation;

/*
  ReviewRepository
  ----------------------------
  정렬 및 페이지네이션에 활용할 Query DSL 전용 인터페이스 선언
 */
public interface ConversationQdslRepository {
	// 필터링 + 정렬 + 커서 기반 페이지네이션이 적용된 대화 목록 조회
	List<Conversation> searchConversation(ConversationPageRequest conversationPageRequest);

	// 필터링이 조건이 적용된 대화 목록의 전체 개수 조회
	Long countConversation(ConversationPageRequest conversationPageRequest);
}
