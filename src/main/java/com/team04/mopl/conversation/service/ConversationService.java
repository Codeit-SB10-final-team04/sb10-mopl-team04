package com.team04.mopl.conversation.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team04.mopl.conversation.dto.request.ConversationCreateRequest;
import com.team04.mopl.conversation.dto.response.ConversationDto;
import com.team04.mopl.conversation.exception.ConversationErrorCode;
import com.team04.mopl.conversation.exception.ConversationException;
import com.team04.mopl.conversation.repository.ConversationRepository;
import com.team04.mopl.user.entity.User;
import com.team04.mopl.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ConversationService {

	private final ConversationRepository conversationRepository;
	private final UserRepository userRepository;

	public ConversationDto createConversation(ConversationCreateRequest conversationCreateRequest, UUID currentUserId) {
		log.info("[CONVERSATION CREATE] 대화 생성 시작: requestUserid={}, withUserId={}",
			currentUserId, conversationCreateRequest.withUserId());

		// 1. 유효성 검증: 개인 채팅방 생성 시도
		validateSelfConversation(currentUserId, conversationCreateRequest.withUserId());

		// 2. 유효성 검증: 요청자 및 사용자 존재 여부
		User requestUser = getUserEntityOrThrow(currentUserId);
		User withUser = getUserEntityOrThrow(conversationCreateRequest.withUserId());

		// 3. 유효성 검증: 중복 검사

		// 3. 대화 생성 및 저장 (try-catch문으로 동시성 방어)

		return null;
	}

	// 유효성 검증: 개인 채팅방 생성 검증
	// TODO: Security 도입 시 @PreAuthorize 로 대체
	private void validateSelfConversation(UUID requestUserId, UUID userId) {
		if (requestUserId.equals(userId)) {
			throw new ConversationException(ConversationErrorCode.CONVERSATION_CANNOT_CHAT_WITH_SELF)
				.addDetail("requestUserId", requestUserId)
				.addDetail("userId", userId);
		}
	}

	// 사용자 엔티티 반환
	private User getUserEntityOrThrow(UUID userId) {
		return userRepository.findById(userId)
			// TODO: User 도메인의 최상위 예외 클래스 구현 시 주석 제거 예정
			.orElseThrow(/*() -> new Userxception(
				UserErrorCode
			)*/);
	}
}
