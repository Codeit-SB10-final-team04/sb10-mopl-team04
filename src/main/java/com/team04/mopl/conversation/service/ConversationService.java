package com.team04.mopl.conversation.service;

import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team04.mopl.common.dto.UserSummary;
import com.team04.mopl.conversation.dto.request.ConversationCreateRequest;
import com.team04.mopl.conversation.dto.response.ConversationDto;
import com.team04.mopl.conversation.entity.Conversation;
import com.team04.mopl.conversation.entity.ConversationParticipant;
import com.team04.mopl.conversation.exception.ConversationErrorCode;
import com.team04.mopl.conversation.exception.ConversationException;
import com.team04.mopl.conversation.mapper.ConversationMapper;
import com.team04.mopl.conversation.mapper.ConversationParticipantMapper;
import com.team04.mopl.conversation.repository.ConversationParticipantRepository;
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
	private final ConversationParticipantRepository conversationParticipantRepository;
	private final UserRepository userRepository;

	private final ConversationMapper conversationMapper;
	private final ConversationParticipantMapper conversationParticipantMapper;

	public ConversationDto createConversation(ConversationCreateRequest conversationCreateRequest, UUID currentUserId) {
		log.info("[CONVERSATION CREATE] 대화 생성 시작: requestUserid={}, withUserId={}",
			currentUserId, conversationCreateRequest.withUserId());

		// 1. 유효성 검증: 개인 채팅방 생성 시도
		validateSelfConversation(currentUserId, conversationCreateRequest.withUserId());

		// 2. 유효성 검증: 요청자 및 사용자 존재 여부
		User requestUser = getUserEntityOrThrow(currentUserId);
		User withUser = getUserEntityOrThrow(conversationCreateRequest.withUserId());

		// 3. 유효성 검증: 중복 검사
		validateDuplicateConversation(requestUser.getId(), withUser.getId());

		// 4. 대화 생성 및 저장 (try-catch문으로 동시성 방어)
		// TODO: 분산 환경에서의 동시성 이슈를 해결하기 위한 Redis 분산 락(Redisson 등) 적용 예정 (심화)
		// 분산 락 적용 시, DB 제약조건 예외를 잡는 현재의 catch 블록은 제거 후 로직 개선
		try {
			// 대화방 생성 및 저장
			Conversation newConversation = Conversation.create();
			conversationRepository.save(newConversation);

			// 대화 참여자 생성 및 저장
			createConversationParticipant(newConversation, requestUser, withUser);

			log.info("[CONVERSATION CREATE] 대화 생성 완료: conversationId={}",
				newConversation.getId());

			// 응답 DTO 변환 (대화방, 대화 상대 정보, 마지막 메시지 내용)
			// 대화방 생성 로직이므로 마지막 메시지 내용은 null 처리
			UserSummary with = getUserSummary(withUser);
			return conversationMapper.toDto(newConversation, with, null);

		} catch (DataIntegrityViolationException e) {
			// DB 제약조건 위반 시, 이미 중복인 상황으로 간주
			throw new ConversationException(ConversationErrorCode.CONVERSATION_ALREADY_EXISTS)
				.addDetail("requestUserId", currentUserId)
				.addDetail("withUserId", conversationCreateRequest.withUserId());
		}
	}

	// 대화 참여자 생성 및 저장
	private void createConversationParticipant(
		Conversation conversation,
		User requestUser,
		User withUser
	) {
		List<ConversationParticipant> participants = List.of(
			conversationParticipantMapper.toEntity(conversation, requestUser),
			conversationParticipantMapper.toEntity(conversation, withUser)
		);

		conversationParticipantRepository.saveAll(participants);
	}

	// 유효성 검증: 대화 중복 검사
	private void validateDuplicateConversation(UUID requestUserId, UUID withUserId) {
		conversationParticipantRepository.findExistingConversationId(requestUserId, withUserId)
			.ifPresent(conversationId -> {
				throw new ConversationException(ConversationErrorCode.CONVERSATION_ALREADY_EXISTS)
					.addDetail("existingConversationId", conversationId);
			});
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

	// 사용자 요약 정보 반환
	private UserSummary getUserSummary(User user) {
		return new UserSummary(
			user.getId(),
			user.getName(),
			user.getProfileImageUrl()
		);
	}
}
