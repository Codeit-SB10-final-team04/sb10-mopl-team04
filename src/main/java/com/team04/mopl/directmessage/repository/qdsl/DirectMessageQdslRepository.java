package com.team04.mopl.directmessage.repository.qdsl;

import java.util.List;
import java.util.UUID;

import com.team04.mopl.directmessage.dto.request.DirectMessagePagedRequest;
import com.team04.mopl.directmessage.entity.DirectMessage;

/*
  DirectMessageQdslRepository
  ----------------------------
  정렬 및 페이지네이션에 활용할 Query DSL 전용 인터페이스 선언
 */
public interface DirectMessageQdslRepository {
	// 정렬 + 커서 페이지네이션이 적용된 DM 목록 조회
	List<DirectMessage> findDirectMessagesByCursor(
		UUID conversationId,
		DirectMessagePagedRequest directMessagePagedRequest,
		UUID requestId
	);

	// DM 목록의 전체 개수 조회
	Long countDirectMessage(
		UUID conversationId,
		DirectMessagePagedRequest directMessagePagedRequest,
		UUID requestId
	);
}
