package com.team04.mopl.conversation.repository.qdsl;

import java.util.List;
import java.util.UUID;

import com.team04.mopl.conversation.entity.Conversation;

/*
  ConversationQdslRepository
  ----------------------------
   DB 탐색을 위한 Query DSL 전용 인터페이스 선언
 */
public interface ConversationQdslRepository {
	// 필터링 + 정렬 + 커서 기반 페이지네이션이 적용된 대화 목록 조회
	List<Conversation> findAllByIdIn(List<UUID> conversationIds);
}
