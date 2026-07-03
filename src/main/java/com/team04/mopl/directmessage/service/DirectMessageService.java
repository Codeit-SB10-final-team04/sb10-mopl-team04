package com.team04.mopl.directmessage.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team04.mopl.conversation.entity.Conversation;
import com.team04.mopl.conversation.exception.ConversationErrorCode;
import com.team04.mopl.conversation.exception.ConversationException;
import com.team04.mopl.conversation.repository.ConversationParticipantRepository;
import com.team04.mopl.conversation.repository.ConversationRepository;
import com.team04.mopl.directmessage.dto.request.DirectMessagePagedRequest;
import com.team04.mopl.directmessage.dto.response.CursorResponseDirectMessageDto;
import com.team04.mopl.directmessage.entity.DirectMessage;
import com.team04.mopl.directmessage.exception.DirectMessageErrorCode;
import com.team04.mopl.directmessage.exception.DirectMessageException;
import com.team04.mopl.directmessage.repository.DirectMessageRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DirectMessageService {

	private final DirectMessageRepository directMessageRepository;
	private final ConversationRepository conversationRepository;
	private final ConversationParticipantRepository conversationParticipantRepository;

	// DM 읽음 상태 생성
	@Transactional
	public void markAsRead(UUID conversationId, UUID directMessageId, UUID requestUserId) {
		log.info("[DM_CREATE_READ_STATUS] DM 읽음 상태 생성 시작: conversationId={}, directMessageId={}", conversationId,
			directMessageId);

		// 1. 유효성 검증: 대화 존재 유무
		Conversation conversation = getConversationEntityOrThrow(conversationId);

		// 2. 유효성 검증: 특정 대화방 참가자 여부
		validateParticipant(conversation.getId(), requestUserId);

		// 3. 유효성 검증: DM 존재 유무
		DirectMessage directMessage = getDirectMessageEntityOrThrow(directMessageId);

		// 4. 유효성 검증: 해당 대화 내 DM 존재 유무
		validateMessageInConversation(directMessage, conversation.getId());

		// 5. 유효성 검증: DM 수신인 여부
		validateReceiver(directMessage, requestUserId);

		// 6. DM 읽음 처리 및 저장
		directMessage.markAsRead();

		log.info("[DM_CREATE_READ_STATUS] DM 읽음 상태 생성 완료: conversationId={}, directMessageId={}",
			conversationId, directMessageId);
	}

	// DM 목록 조회
	public CursorResponseDirectMessageDto findAll(
		UUID conversationId,
		DirectMessagePagedRequest directMessagePagedRequest,
		UUID userId
	) {

	}

	// 유효성 검증: 해당 대화 내 DM 존재 유무
	private void validateMessageInConversation(DirectMessage directMessage, UUID conversationId) {
		if (!directMessage.getConversation().getId().equals(conversationId)) {
			throw new DirectMessageException(DirectMessageErrorCode.DM_NOT_IN_CONVERSATION);
		}
	}

	// 유효성 검증: 특정 대화방 참가자 여부
	private void validateParticipant(UUID conversationId, UUID userId) {
		boolean isParticipant = conversationParticipantRepository.findByConversationId(conversationId).stream()
			.anyMatch(participant -> participant.getUser().getId().equals(userId));

		if (!isParticipant) {
			throw new ConversationException(ConversationErrorCode.CONVERSATION_ACCESS_DENIED);
		}
	}

	// 유효성 검증: DM 수신인 여부
	private void validateReceiver(DirectMessage directMessage, UUID userId) {
		if (!directMessage.getReceiver().getId().equals(userId)) {
			throw new DirectMessageException(DirectMessageErrorCode.DM_ACCESS_DENIED);
		}
	}

	// DM 엔티티 반환
	private DirectMessage getDirectMessageEntityOrThrow(UUID directMessageId) {
		return directMessageRepository.findById(directMessageId)
			.orElseThrow(() -> new DirectMessageException(DirectMessageErrorCode.DM_NOT_FOUND));
	}

	// 대화 엔티티 반환
	private Conversation getConversationEntityOrThrow(UUID conversationId) {
		return conversationRepository.findById(conversationId)
			.orElseThrow(() -> new ConversationException(ConversationErrorCode.CONVERSATION_NOT_FOUND));
	}
}
