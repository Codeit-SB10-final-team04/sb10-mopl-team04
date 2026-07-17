package com.team04.mopl.directmessage.service;

import java.util.List;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team04.mopl.conversation.entity.Conversation;
import com.team04.mopl.conversation.entity.ConversationParticipant;
import com.team04.mopl.conversation.exception.ConversationErrorCode;
import com.team04.mopl.conversation.exception.ConversationException;
import com.team04.mopl.conversation.repository.ConversationParticipantRepository;
import com.team04.mopl.conversation.repository.ConversationRepository;
import com.team04.mopl.directmessage.dto.request.DirectMessagePageRequest;
import com.team04.mopl.directmessage.dto.request.DirectMessageSendRequest;
import com.team04.mopl.directmessage.dto.response.CursorResponseDirectMessageDto;
import com.team04.mopl.directmessage.dto.response.DirectMessageDto;
import com.team04.mopl.directmessage.entity.DirectMessage;
import com.team04.mopl.directmessage.event.DirectMessageCreatedEvent;
import com.team04.mopl.directmessage.event.DirectMessageReadEvent;
import com.team04.mopl.directmessage.event.DirectMessageSentEvent;
import com.team04.mopl.directmessage.exception.DirectMessageErrorCode;
import com.team04.mopl.directmessage.exception.DirectMessageException;
import com.team04.mopl.directmessage.mapper.DirectMessageMapper;
import com.team04.mopl.directmessage.redis.DirectMessageRedisStore;
import com.team04.mopl.directmessage.repository.DirectMessageRepository;
import com.team04.mopl.user.entity.User;

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

	private final DirectMessageMapper directMessageMapper;

	private final DirectMessageRedisStore directMessageRedisStore;

	private final ApplicationEventPublisher eventPublisher;

	// DM 생성
	@Transactional
	public DirectMessageDto create(
		UUID conversationId,
		DirectMessageSendRequest directMessageSendRequest,
		UUID senderId
	) {
		log.info("[DM_CREATE] DM 생성 시작: conversationId={}, senderId={}", conversationId, senderId);

		// 1. 유효성 검증: 대화 존재 여부
		Conversation conversation = getConversationEntityOrThrow(conversationId);

		// 2. 대화 참여자 목록 조회
		List<ConversationParticipant> participants = getParticipants(conversationId);

		// 3. 송신자 조회
		User sender = extractSenderOrThrow(participants, senderId);

		// 4. 수신자 조회
		User receiver = extractReceiverOrThrow(participants, senderId);

		// 5. DM 엔티티 생성 및 저장
		DirectMessage newDirectMessage = directMessageMapper.toEntity(
			conversation,
			sender,
			receiver,
			directMessageSendRequest
		);
		directMessageRepository.save(newDirectMessage);

		// 6. DTO 전환
		DirectMessageDto directMessageDto = directMessageMapper.toDto(newDirectMessage);

		// 7. SSE 및 Redis 동기화를 위한 이벤트 발행
		eventPublisher.publishEvent(new DirectMessageCreatedEvent(
			receiver.getId(),
			newDirectMessage.getId(),
			directMessageDto
		));

		// 8. ES 서버 동기화를 위한 이벤트 발행
		eventPublisher.publishEvent(new DirectMessageSentEvent(
			conversationId,
			newDirectMessage.getId(),
			newDirectMessage.getContent())
		);

		log.info("[DM_CREATE] DM 생성 완료: conversationId={}, senderId={}, dmId={}",
			conversationId, senderId, newDirectMessage.getId());

		return directMessageDto;
	}

	// 송신자 추출 및 검증
	private User extractSenderOrThrow(List<ConversationParticipant> participants, UUID senderId) {
		return participants.stream()
			.map(ConversationParticipant::getUser)
			.filter(user -> user.getId().equals(senderId))
			.findFirst()
			// 대화 참여자 목록에 송신자가 없을 경우, 예외 처리
			.orElseThrow(() -> new ConversationException(ConversationErrorCode.CONVERSATION_ACCESS_DENIED));
	}

	// 수신자 추출 및 검증
	private User extractReceiverOrThrow(List<ConversationParticipant> participants, UUID senderId) {
		return participants.stream()
			.map(ConversationParticipant::getUser)
			.filter(user -> !user.getId().equals(senderId))
			.findFirst()
			.orElseThrow(() -> new ConversationException(ConversationErrorCode.CONVERSATION_PARTICIPANT_NOT_FOUND));
	}

	// DM 읽음 상태 생성
	@Transactional
	public void markAsRead(
		UUID conversationId,
		UUID directMessageId,
		UUID requestUserId
	) {
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

		// 5. DM 읽음 처리 및 저장
		directMessage.markAsRead();

		// 6. 읽음 처리 Redis 동기화를 위한 이벤트 발행
		eventPublisher.publishEvent(new DirectMessageReadEvent(
			requestUserId,
			conversationId,
			directMessageId
		));

		log.info("[DM_CREATE_READ_STATUS] DM 읽음 상태 생성 완료: conversationId={}, directMessageId={}",
			conversationId, directMessageId);
	}

	// DM 목록 조회 (정렬 + 커서 페이지네이션)
	public CursorResponseDirectMessageDto findAll(
		UUID conversationId,
		DirectMessagePageRequest directMessagePageRequest,
		UUID requestUserId
	) {
		log.debug("[DM_FIND_ALL] DM 목록 조회 시작");

		// 1. 유효성 검증: 대화 존재 유무
		Conversation conversation = getConversationEntityOrThrow(conversationId);

		// 2. 유효성 검증: 특정 대화방 참가자 여부
		validateParticipant(conversation.getId(), requestUserId);

		// 3. 정렬 + 커서 기반 페이지네이션이 적용된 DM 리스트
		List<DirectMessage> directMessages = /*!StringUtils.hasText(directMessagePageRequest.cursor())
			? fetchFirstPageMessages(conversation.getId(), requestUserId, directMessagePageRequest)
			: */ directMessageRepository.findDirectMessagesByCursor(conversation.getId(), directMessagePageRequest);

		// 4. DM 전체 개수 조회: Cache-Aside 로직 적용
		Long totalCount = directMessageRedisStore.getRoomMessageTotalCount(conversation.getId());

		// Redis 조회 결과가 없는 경우, DB 조회
		if (totalCount == null) {
			List<DirectMessage> allMessages = directMessageRepository.findAllByConversationId(conversation.getId());
			totalCount = (long)allMessages.size();

			// Redis 백필
			directMessageRedisStore.initDirectMessages(conversation.getId(), allMessages);
		}

		// 5. 다음 페이지 유무 확인 및 limit (기본값: 10) 만큼 자르기
		boolean hasNext = directMessages.size() > directMessagePageRequest.limit();
		List<DirectMessage> pagedDirectMessages = hasNext
			? directMessages.subList(0, directMessagePageRequest.limit())
			: directMessages;

		// 6. 다음 커서 값 계산 (메인 커서, 보조 커서)
		String nextCursor = null;
		UUID nextIdAfter = null;

		// 조회 결과로 DM 목록이 존재하고, 다음 페이지가 존재할 경우에만 다음 커서 값 지정
		if (!pagedDirectMessages.isEmpty() && hasNext) {
			// 마지막 요소
			DirectMessage lastDirectMessage = pagedDirectMessages.get(pagedDirectMessages.size() - 1);

			nextCursor = lastDirectMessage.getCreatedAt().toString();
			nextIdAfter = lastDirectMessage.getId();
		}

		// 7. DirectMessage -> DirectMessageDto
		List<DirectMessageDto> data = directMessageMapper.toDtoList(pagedDirectMessages);

		log.debug("[DM_FIND_ALL] DM 목록 조회 완료");

		// 8. CursorResponseDirectMessageDto 전환
		return directMessageMapper.toCursorPageResponse(
			data,
			nextCursor,
			nextIdAfter,
			hasNext,
			totalCount,
			directMessagePageRequest.sortBy(),
			directMessagePageRequest.sortDirection().name()
		);
	}

	// 유효성 검증: 해당 대화 내 DM 존재 유무
	private void validateMessageInConversation(DirectMessage directMessage, UUID conversationId) {
		if (!directMessage.getConversation().getId().equals(conversationId)) {
			throw new DirectMessageException(DirectMessageErrorCode.DM_NOT_IN_CONVERSATION)
				.addDetail("conversationId", conversationId);
		}
	}

	// 유효성 검증: 특정 대화방 참가자 여부
	private void validateParticipant(UUID conversationId, UUID userId) {
		boolean isParticipant = conversationParticipantRepository.findByConversationId(conversationId).stream()
			.anyMatch(participant -> participant.getUser().getId().equals(userId));

		if (!isParticipant) {
			throw new ConversationException(ConversationErrorCode.CONVERSATION_ACCESS_DENIED)
				.addDetail("conversationId", conversationId)
				.addDetail("userId", userId);
		}
	}

	// DM 엔티티 반환
	private DirectMessage getDirectMessageEntityOrThrow(UUID directMessageId) {
		return directMessageRepository.findById(directMessageId)
			.orElseThrow(() -> new DirectMessageException(DirectMessageErrorCode.DM_NOT_FOUND)
				.addDetail("directMessageId", directMessageId));
	}

	// 대화 엔티티 반환
	private Conversation getConversationEntityOrThrow(UUID conversationId) {
		return conversationRepository.findById(conversationId)
			.orElseThrow(() -> new ConversationException(ConversationErrorCode.CONVERSATION_NOT_FOUND)
				.addDetail("conversationId", conversationId));
	}

	// 대화 참여자 목록 반환
	private List<ConversationParticipant> getParticipants(UUID conversationId) {
		return conversationParticipantRepository.findByConversationId(conversationId);
	}
}