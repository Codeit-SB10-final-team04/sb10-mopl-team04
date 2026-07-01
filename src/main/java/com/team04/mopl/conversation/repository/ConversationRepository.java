package com.team04.mopl.conversation.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.team04.mopl.conversation.entity.Conversation;
import com.team04.mopl.conversation.repository.qdsl.ConversationQdslRepository;

public interface ConversationRepository extends JpaRepository<Conversation, UUID>, ConversationQdslRepository {
}
