package com.team04.mopl.conversation.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.team04.mopl.conversation.entity.Conversation;

public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

	Optional<Conversation> findByIdAndUserId(UUID conversationId);
}
