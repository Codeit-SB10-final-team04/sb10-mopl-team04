package com.team04.mopl.directmessage.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.team04.mopl.directmessage.entity.DirectMessage;

public interface DirectMessageRepository extends JpaRepository<DirectMessage, UUID> {

	// 최근 메시지 단건 조회
	Optional<DirectMessage> findTopByConversationIdOrderByCreatedAtDescIdDesc(UUID conversationId);

	// 안 읽음 메시지 존재 유무
	boolean existsByConversationIdAndReceiverIdAndReadFalse(UUID conversationId, UUID receiverId);

	// 최근 메시지 다건 조회: 대화 ID 목록에 해당하는 대화방의 마지막 메시지 조회
	@Query("SELECT dm FROM DirectMessage dm "
		+ "WHERE dm.id IN ("
		+ "    SELECT MAX(dm2.id) FROM DirectMessage dm2 "
		+ "    WHERE dm2.conversation.id IN :conversationIds "
		+ "    GROUP BY dm2.conversation.id"
		+ ")")
	List<DirectMessage> findLatestMessagesByConversationIds(@Param("conversationIds") List<UUID> conversationIds);

	// 안 읽음 여부 다건 조회: 대화 ID 목록에 대당하는 대화방의 안 읽음 여부 조회
	@Query("SELECT DISTINCT dm.conversation.id FROM DirectMessage dm "
		+ "WHERE dm.conversation.id IN :conversationIds "
		+ "AND dm.receiver.id = :receiverId "
		+ "AND dm.read = false")
	Set<UUID> findUnreadConversationIds(
		@Param("conversationIds") List<UUID> conversationIds,
		@Param("receiverId") UUID receiverId
	);
}
