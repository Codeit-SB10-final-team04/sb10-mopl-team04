package com.team04.mopl.directmessage.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.team04.mopl.directmessage.entity.DirectMessage;

public interface DirectMessageRepository extends JpaRepository<DirectMessage, UUID> {

	// 최근 메시지 단건 조회
	Optional<DirectMessage> findTopByConversationIdOrderByCreatedAtDescIdDesc(UUID conversationId);

	// 안 읽음 메시지 존재 유무
	boolean existsByConversationIdAndReceiverIdAndReadFalse(UUID conversationId, UUID receiverId);
}
