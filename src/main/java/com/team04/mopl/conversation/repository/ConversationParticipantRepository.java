package com.team04.mopl.conversation.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.team04.mopl.conversation.entity.ConversationParticipant;

public interface ConversationParticipantRepository extends JpaRepository<ConversationParticipant, UUID> {

	// 특정 대화방의 참여자 목록 조회: 특정 대화에 참여하는 두 명의 사용자 목록 반환
	List<ConversationParticipant> findByConversationId(UUID conversationId);

	// 대화 참여자 목록 조회: 대화 ID 목록에 해당하는 사용자 목록 반환
	@EntityGraph(attributePaths = {"user"})
	List<ConversationParticipant> findByConversationIdIn(List<UUID> conversationIds);

	// 유효성 검증: 대화 중복 검사
	// 두 사용자가 공통된 대화 ID를 가지고 있다면, 해당 대화방의 ID 값을 반환
	@Query("SELECT p1.conversation.id FROM ConversationParticipant p1 "
		+ "JOIN ConversationParticipant p2 ON p1.conversation.id = p2.conversation.id "
		+ "WHERE p1.user.id = :userId1 AND p2.user.id = :userId2")
	Optional<UUID> findExistingConversationId(@Param("userId1") UUID userId1, @Param("userId2") UUID userId2);
}
