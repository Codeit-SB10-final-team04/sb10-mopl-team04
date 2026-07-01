package com.team04.mopl.conversation.service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team04.mopl.auth.security.MoplUserDetails;
import com.team04.mopl.common.dto.UserSummary;
import com.team04.mopl.conversation.dto.request.ConversationCreateRequest;
import com.team04.mopl.conversation.dto.request.ConversationSearchRequest;
import com.team04.mopl.conversation.dto.response.ConversationDto;
import com.team04.mopl.conversation.dto.response.CursorResponseConversationDto;
import com.team04.mopl.conversation.entity.Conversation;
import com.team04.mopl.conversation.entity.ConversationParticipant;
import com.team04.mopl.conversation.exception.ConversationErrorCode;
import com.team04.mopl.conversation.exception.ConversationException;
import com.team04.mopl.conversation.mapper.ConversationMapper;
import com.team04.mopl.conversation.mapper.ConversationParticipantMapper;
import com.team04.mopl.conversation.repository.ConversationParticipantRepository;
import com.team04.mopl.conversation.repository.ConversationRepository;
import com.team04.mopl.directmessage.dto.response.DirectMessageDto;
import com.team04.mopl.directmessage.entity.DirectMessage;
import com.team04.mopl.directmessage.mapper.DirectMessageMapper;
import com.team04.mopl.directmessage.repository.DirectMessageRepository;
import com.team04.mopl.user.entity.User;
import com.team04.mopl.user.exception.UserErrorCode;
import com.team04.mopl.user.exception.UserException;
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
	private final DirectMessageRepository directMessageRepository;
	private final UserRepository userRepository;

	private final ConversationMapper conversationMapper;
	private final ConversationParticipantMapper conversationParticipantMapper;
	private final DirectMessageMapper directMessageMapper;

	// 대화 생성
	@Transactional
	@PreAuthorize("#conversationCreateRequest.withUserId() != #moplUserDetails.userId")
	public ConversationDto createConversation(
		ConversationCreateRequest conversationCreateRequest,
		MoplUserDetails moplUserDetails
	) {
		// 1. 로그인 정보로부터 요청자의 ID 추출
		UUID requestUserId = moplUserDetails.getUserId();

		log.info("[CONVERSATION_CREATE] 대화 생성 시작: requestUserid={}, withUserId={}",
			requestUserId, conversationCreateRequest.withUserId());

		// 2. 유효성 검증: 요청자 및 사용자 존재 여부
		User requestUser = getUserEntityOrThrow(requestUserId);
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

			log.info("[CONVERSATION_CREATE] 대화 생성 완료: conversationId={}",
				newConversation.getId());

			// 응답 DTO 변환 (대화방, 대화 상대 정보, 마지막 메시지 내용, 안 읽음 여부)
			// 대화방 생성 로직이므로 마지막 메시지 내용은 null, 안 읽음 여부는 false 처리
			UserSummary with = getUserSummary(withUser);
			return conversationMapper.toDto(newConversation, with, null, false);

		} catch (DataIntegrityViolationException e) {
			// DB 제약조건 위반 시, 이미 중복인 상황으로 간주
			throw new ConversationException(ConversationErrorCode.CONVERSATION_ALREADY_EXISTS)
				.addDetail("requestUserId", requestUserId)
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

	// 대화 단건 조회
	public ConversationDto findConversationById(UUID conversationId, MoplUserDetails moplUserDetails) {
		log.debug("[CONVERSATION_FIND] 대화 단건 조회 시작: conversationId={}", conversationId);

		// 1. 로그인 정보로부터 요청자 ID 추출
		UUID requestUserId = moplUserDetails.getUserId();

		// 2. 유효성 검증: 대화 존재 여부
		Conversation conversation = getConversationEntityOrThrow(conversationId);

		// 3, 유효성 검증: 대화 상대 존재 여부
		User withUser = getWithUserEntityOrThrow(conversation.getId(), requestUserId);

		// 3. DTO 변환
		return mapToConversationDto(conversation, withUser, requestUserId);
	}

	// 대화 상대 정보 조회
	private User getWithUserEntityOrThrow(UUID conversationId, UUID requestUserId) {
		List<ConversationParticipant> participants =
			conversationParticipantRepository.findByConversationId(conversationId);

		validateParticipants(participants, requestUserId);

		return participants.stream()
			.map(ConversationParticipant::getUser)
			.filter(user -> !user.getId().equals(requestUserId))
			.findFirst()
			.orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
	}

	// 특정 사용자와의 대화 조회
	public ConversationDto findConversationByUserId(UUID userId, MoplUserDetails moplUserDetails) {
		log.debug("[CONVERSATION_FIND_BY_USER_ID] 특정 사용자와의 대화 조회 시작: userId={}", userId);

		// 1. 로그인 정보로부터 요청자 ID 추출
		UUID requestUserId = moplUserDetails.getUserId();

		// 2. 유효성 검증: 대화 상대 존재 여부
		User withUser = getUserEntityOrThrow(userId);

		// 3. 유효성 검증: 자기 자신 조회
		validateSelfReadConversation(requestUserId, withUser.getId());

		// 4. 유효성 검증: 대화 존재 유무
		UUID conversationId = findExistingConversationId(requestUserId, withUser.getId());
		Conversation conversation = getConversationEntityOrThrow(conversationId);

		log.debug("[CONVERSATION_FIND_BY_USER_ID] 특정 사용자와의 대화 조회 완료: conversationId={}", conversationId);
		return mapToConversationDto(conversation, withUser, requestUserId);
	}

	// 대화 ID 반환
	private UUID findExistingConversationId(UUID requestUserId, UUID withUserId) {
		return conversationParticipantRepository.findExistingConversationId(requestUserId, withUserId)
			.orElseThrow(() -> new ConversationException(ConversationErrorCode.CONVERSATION_NOT_FOUND));
	}

	// 대화 목록 조회 (정렬 + 필터링 + 커서 페이지네이션)
	private CursorResponseConversationDto findAll(
		ConversationSearchRequest request,
		UUID requestUserId
	) {
		log.debug("[CONVERSATION_FIND_SEARCH] 대화 목록 조회 시작: keyword={}", request.keywordLike());

		// 1. 필터링 + 정렬 + 커서 기반 페이지네이션이 적용된 대화 리스트

		// 대화 전체 개수 조회

		// 2. 다음 페이지 유무 확인

		// 3. 다음 커서 값 계산 (메인 커서, 보조 커서)

		// 4. Conversation -> ConversationDto (내부 메서드 활용)

		// 5. CursorResponseConversationDto 전환 (Mapper 활용)

		log.debug("[CONVERSATION_FIND_SEARCH] 대화 목록 조회 완료: keyword={}", request.keywordLike());

		return null;
	}

	// 유효성 검증: 대화 중복 검사
	private void validateDuplicateConversation(UUID requestUserId, UUID withUserId) {
		conversationParticipantRepository.findExistingConversationId(requestUserId, withUserId)
			.ifPresent(conversationId -> {
				throw new ConversationException(ConversationErrorCode.CONVERSATION_ALREADY_EXISTS)
					.addDetail("existingConversationId", conversationId);
			});
	}

	// 유효성 검증: 특정 대화방 참가자 여부
	private void validateParticipants(List<ConversationParticipant> participants, UUID requestUserId) {
		boolean isParticipant = participants.stream()
			.anyMatch(participant -> participant.getUser().getId().equals(requestUserId));

		if (!isParticipant) {
			throw new ConversationException(ConversationErrorCode.CONVERSATION_ACCESS_DENIED);
		}
	}

	// 유효성 검증: 자기 자신 조회
	private void validateSelfReadConversation(UUID requestUserId, UUID withUserId) {
		if (requestUserId.equals(withUserId)) {
			throw new ConversationException(ConversationErrorCode.CONVERSATION_SELF_SELECT_MOT_ALLOWED);
		}
	}

	// 대화 엔티티 반환
	private Conversation getConversationEntityOrThrow(UUID conversationId) {
		return conversationRepository.findById(conversationId)
			.orElseThrow(() -> new ConversationException(ConversationErrorCode.CONVERSATION_NOT_FOUND)
				.addDetail("conversationId", conversationId));
	}

	// 사용자 엔티티 반환
	private User getUserEntityOrThrow(UUID userId) {
		return userRepository.findById(userId)
			.orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND)
				.addDetail("userId", userId));
	}

	// 사용자 요약 정보 반환
	private UserSummary getUserSummary(User user) {
		return new UserSummary(
			user.getId(),
			user.getName(),
			user.getProfileImageUrl()
		);
	}

	// DTO 변환 로직
	private ConversationDto mapToConversationDto(Conversation conversation, User withUser, UUID requestUserId) {
		// 1. 대화 상대방 정보 조회
		UserSummary with = getUserSummary(withUser);

		// 2. 마지막 메시지 내용 조회
		DirectMessageDto latestMessage = getLatestMessageEntity(conversation.getId())
			.map(directMessageMapper::toDto)
			.orElse(null);

		// 3. 안 읽음 여부 판단
		boolean hasUnread = hasUnreadMessage(conversation.getId(), requestUserId);

		return conversationMapper.toDto(conversation, with, latestMessage, hasUnread);
	}

	// 마지막 메시지 조회
	private Optional<DirectMessage> getLatestMessageEntity(UUID conversationId) {
		return directMessageRepository.findTopByConversationIdOrderByCreatedAtDescIdDesc(conversationId);
	}

	// 안 읽은 메시지 여부 확인
	private boolean hasUnreadMessage(UUID conversationId, UUID receiverId) {
		return directMessageRepository.existsByConversationIdAndReceiverIdAndReadFalse(conversationId, receiverId);
	}
}
