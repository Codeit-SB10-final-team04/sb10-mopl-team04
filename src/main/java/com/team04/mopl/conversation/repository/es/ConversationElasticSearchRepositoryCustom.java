package com.team04.mopl.conversation.repository.es;

import java.util.List;
import java.util.UUID;

import com.team04.mopl.conversation.document.ConversationDocument;
import com.team04.mopl.conversation.dto.request.ConversationPageRequest;

/*
  ConversationElasticSearchRepositoryCustom
  ------------------------------------------
  필터링, 정렬 및 페이지네이션에 활용할 Elastic Search 전용 인터페이스 선언
 */
public interface ConversationElasticSearchRepositoryCustom {

	// 필터링 + 정렬 + 커서 기반 페이지네이션이 적용된 대화 인덱스 목록 조회
	List<ConversationDocument> searchConversation(
		ConversationPageRequest request,
		UUID requestUserId
	);

	// 필터링이 조건이 적용된 대화 인덱스 목록의 전체 개수 조회
	long countConversation(ConversationPageRequest request, UUID requestUserId);
}
