package com.team04.mopl.directmessage.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.team04.mopl.directmessage.entity.DirectMessage;
import com.team04.mopl.directmessage.repository.qdsl.DirectMessageQdslRepository;

public interface DirectMessageRepository extends JpaRepository<DirectMessage, UUID>, DirectMessageQdslRepository {

	// 최근 메시지 단건 조회
	Optional<DirectMessage> findTopByConversationIdOrderByCreatedAtDescIdDesc(UUID conversationId);

	// 특정 수신자의 마지막 이벤트 메시지 단건 조회
	Optional<DirectMessage> findByIdAndReceiverId(UUID id, UUID receiverId);

	// 안 읽음 메시지 존재 유무
	boolean existsByConversationIdAndReceiverIdAndReadFalse(UUID conversationId, UUID receiverId);

	// 최근 메시지 다건 조회: 대화 ID 목록에 해당하는 대화방의 마지막 메시지 조회
	@Query("SELECT dm FROM DirectMessage dm "
		+ "WHERE dm.createdAt = ("
		+ "    SELECT MAX(dm2.createdAt) FROM DirectMessage dm2 "
		+ "    WHERE dm2.conversation.id = dm.conversation.id"
		+ ") "
		+ "AND dm.conversation.id IN :conversationIds")
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

	// 안 읽은 메시지 다건 조회: 특정 메시지 이후에 수신된 미읽음 메시지 목록 조회 (커서 X)
	@Query("SELECT dm FROM DirectMessage dm "
		+ "WHERE dm.receiver.id = :receiverId "
		+ "AND dm.read = false "
		+ "ORDER BY dm.createdAt ASC")
	List<DirectMessage> findUnreadMessages(
		@Param("receiverId") UUID receiverId,
		Pageable pageable
	);

	// 안 읽은 메시지 다건 조회: 특정 메시지 이후에 수신된 미읽음 메시지 목록 조회 (커서 O)
	@Query("SELECT dm FROM DirectMessage dm "
		+ "WHERE dm.receiver.id = :receiverId "
		+ "AND dm.read = false "
		+ "AND (dm.createdAt > :createdAt OR (dm.createdAt = :createdAt AND dm.id > :lastId)) "
		+ "ORDER BY dm.createdAt ASC, dm.id ASC")
	List<DirectMessage> findUnreadMessagesAfter(
		@Param("receiverId") UUID receiverId,
		@Param("lastId") UUID lastId,
		@Param("createdAt") Instant createdAt,
		Pageable pageable
	);
}
