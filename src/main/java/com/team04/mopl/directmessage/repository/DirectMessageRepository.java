package com.team04.mopl.directmessage.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.team04.mopl.directmessage.entity.DirectMessage;

public interface DirectMessageRepository extends JpaRepository<DirectMessage, UUID> {

	// 최근 메시지 단건 조회
	Optional<DirectMessage> findTopByConversationIdOrderByCreatedAtDesc(UUID conversationId);
}
