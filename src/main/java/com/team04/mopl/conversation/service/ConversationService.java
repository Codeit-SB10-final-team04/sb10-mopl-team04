package com.team04.mopl.conversation.service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team04.mopl.common.dto.UserSummary;
import com.team04.mopl.conversation.document.ConversationDocument;
import com.team04.mopl.conversation.dto.request.ConversationCreateRequest;
import com.team04.mopl.conversation.dto.request.ConversationPageRequest;
import com.team04.mopl.conversation.dto.response.ConversationDto;
import com.team04.mopl.conversation.dto.response.CursorResponseConversationDto;
import com.team04.mopl.conversation.entity.Conversation;
import com.team04.mopl.conversation.entity.ConversationParticipant;
import com.team04.mopl.conversation.event.ConversationCreatedEvent;
import com.team04.mopl.conversation.exception.ConversationErrorCode;
import com.team04.mopl.conversation.exception.ConversationException;
import com.team04.mopl.conversation.mapper.ConversationMapper;
import com.team04.mopl.conversation.mapper.ConversationParticipantMapper;
import com.team04.mopl.conversation.redis.ConversationRedisStore;
import com.team04.mopl.conversation.repository.ConversationParticipantRepository;
import com.team04.mopl.conversation.repository.ConversationRepository;
import com.team04.mopl.conversation.repository.es.ConversationElasticSearchRepository;
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
	private final ConversationElasticSearchRepository conversationElasticSearchRepository;
	private final DirectMessageRepository directMessageRepository;
	private final UserRepository userRepository;

	private final ConversationRedisStore conversationRedisStore;

	private final ConversationMapper conversationMapper;
	private final ConversationParticipantMapper conversationParticipantMapper;
	private final DirectMessageMapper directMessageMapper;

	private final ApplicationEventPublisher applicationEventPublisher;

	// 대화 생성
	@Transactional
	@PreAuthorize("#conversationCreateRequest.withUserId() != #requestUserId")
	public ConversationDto createConversation(
		ConversationCreateRequest conversationCreateRequest,
		UUID requestUserId
	) {
		log.info("[CONVERSATION_CREATE] 대화 생성 시작: requestUserid={}, withUserId={}",
			requestUserId, conversationCreateRequest.withUserId());

		// 1. 유효성 검증: 요청자 및 사용자 존재 여부
		User requestUser = getUserEntityOrThrow(requestUserId);
		User withUser = getUserEntityOrThrow(conversationCreateRequest.withUserId());

		// 2. 유효성 검증: 중복 검사
		validateDuplicateConversation(requestUser.getId(), withUser.getId());

		// 3. 대화방 생성 및 저장
		Conversation newConversation = Conversation.create();
		conversationRepository.save(newConversation);

		// 4. 대화 참여자 생성 및 저장
		List<ConversationParticipant> participants = createConversationParticipant(
			newConversation,
			requestUser,
			withUser
		);

		// 5. ES 생성 및 Redis 동기화를 위한 이벤트 발행
		List<UUID> participantIds = participants.stream()
			.map(participant -> participant.getUser().getId())
			.toList();

		applicationEventPublisher.publishEvent(
			new ConversationCreatedEvent(
				newConversation.getId(),
				participantIds,
				newConversation.getCreatedAt())
		);

		log.info("[CONVERSATION_CREATE] 대화 생성 완료: conversationId={}",
			newConversation.getId());

		// 6. 대화 상대 정보 조회
		UserSummary with = getUserSummary(withUser);

		// 7. 응답 DTO 변환 (대화방, 대화 상대 정보, 마지막 메시지 내용, 안 읽음 여부)
		return conversationMapper.toDto(newConversation, with, null, false);
	}

	// 대화 참여자 생성 및 저장
	private List<ConversationParticipant> createConversationParticipant(
		Conversation conversation,
		User requestUser,
		User withUser
	) {
		List<ConversationParticipant> participants = List.of(
			conversationParticipantMapper.toEntity(conversation, requestUser),
			conversationParticipantMapper.toEntity(conversation, withUser)
		);

		return conversationParticipantRepository.saveAll(participants);
	}

	// 대화 단건 조회
	public ConversationDto findConversationById(
		UUID conversationId,
		UUID requestUserId
	) {
		log.debug("[CONVERSATION_FIND] 대화 단건 조회 시작: conversationId={}", conversationId);

		// 1. 유효성 검증: 대화 존재 여부
		Conversation conversation = getConversationEntityOrThrow(conversationId);

		// 2. 유효성 검증: 대화 상대 존재 여부
		User withUser = getWithUserEntityOrThrow(conversation.getId(), requestUserId);

		return mapToConversationDto(conversation, withUser, requestUserId);
	}

	// 대화 상대 정보 조회
	private User getWithUserEntityOrThrow(
		UUID conversationId,
		UUID requestUserId
	) {
		// 1. 특정 대화의 참여자 목록 조회 (Redis)
		Set<UUID> participantIds = conversationRedisStore.getParticipants(conversationId);

		List<ConversationParticipant> participants;

		// 특정 대화의 참여자 목록이 없을 경우
		if (participantIds == null) {
			// 특정 대화의 참여자 목록 조회
			participants = conversationParticipantRepository.findByConversationId(conversationId);

			// 대화 참여자 ID 추출 및 목록 변환
			participantIds = participants.stream()
				.map(participant -> participant.getUser().getId())
				.collect(Collectors.toSet());

			// DB 백필
			conversationRedisStore.initParticipants(conversationId, participantIds);
		}

		// 2. 유효성 검증: 요청자의 대화 참여자 소속 여부
		validateParticipantsAccess(participantIds, requestUserId);

		// 3. 대화 상대방 ID 추출
		UUID withUserId = extractWithUserId(participantIds, requestUserId);

		return getUserEntityOrThrow(withUserId);
	}

	// 대화 상대방 ID 추출
	private UUID extractWithUserId(Set<UUID> participantIds, UUID requestUserId) {
		return participantIds.stream()
			.filter(id -> !id.equals(requestUserId))
			.findFirst()
			.orElseThrow(() -> new ConversationException(ConversationErrorCode.CONVERSATION_PARTICIPANT_NOT_FOUND));
	}

	// 특정 사용자와의 대화 조회
	public ConversationDto findConversationByUserId(
		UUID userId,
		UUID requestUserId
	) {
		log.debug("[CONVERSATION_FIND_BY_USER_ID] 특정 사용자와의 대화 조회 시작: userId={}", userId);

		// 1. 유효성 검증: 대화 상대 존재 여부
		User withUser = getUserEntityOrThrow(userId);

		// 2. 유효성 검증: 자기 자신 조회
		validateSelfReadConversation(requestUserId, withUser.getId());

		// 3. 유효성 검증: 대화 존재 유무
		UUID conversationId = findExistingConversationId(requestUserId, withUser.getId());
		Conversation conversation = getConversationEntityOrThrow(conversationId);

		log.debug("[CONVERSATION_FIND_BY_USER_ID] 특정 사용자와의 대화 조회 완료: conversationId={}", conversationId);

		return mapToConversationDto(conversation, withUser, requestUserId);
	}

	// 대화 ID 반환
	private UUID findExistingConversationId(
		UUID requestUserId,
		UUID withUserId
	) {
		// 1. 특정 사용자와의 대화 조회 (Redis)
		UUID conversationId = conversationRedisStore.getConversationId(requestUserId, withUserId);

		// 특정 사용자와의 대화가 존재하지 않을 경우
		if (conversationId == null) {
			conversationId = getConversationIdOrNull(requestUserId, withUserId);

			// DB 백필
			conversationRedisStore.initConversationMapping(requestUserId, withUserId, conversationId);
		}

		// 3. 유효성 검증: 빈 대화방일 경우 예외 발생
		validateConversation(conversationId);

		return conversationId;
	}

	// 대화 목록 조회 (필터링 + 정렬 + 커서 페이지네이션)
	public CursorResponseConversationDto findAll(
		ConversationPageRequest conversationPageRequest,
		UUID requestUserId
	) {
		log.debug("[CONVERSATION_FIND_SEARCH] 대화 목록 조회 시작: keyword={}", conversationPageRequest.keywordLike());

		// 1. 유효성 검증: 정렬 기준
		validateSortField(conversationPageRequest.sortBy());

		// 2. 필터링 + 정렬 + 커서 기반 페이지네이션이 적용된 대화 리스트
		List<ConversationDocument> documents = conversationElasticSearchRepository.searchConversation(
			conversationPageRequest,
			requestUserId
		);

		// 3. 대화 전체 개수 조회
		Long totalCount = conversationElasticSearchRepository.countConversation(conversationPageRequest, requestUserId);

		// 조회 결과가 없을 경우, 불필요한 DB 조회 방지를 위해 빈 응답 객체 반환
		if (documents.isEmpty()) {
			return createEmptyPageResponse(totalCount, conversationPageRequest);
		}

		// 4. 다음 페이지 유무 확인 및 limit (기본값: 10) 만큼 자르기
		boolean hasNext = documents.size() > conversationPageRequest.limit();
		List<ConversationDocument> pagedDocuments = hasNext
			? documents.subList(0, conversationPageRequest.limit())
			: documents;

		// 5. 다음 커서 값 계산 (메인 커서, 보조 커서)
		String nextCursor = null;
		UUID nextIdAfter = null;

		// 조회 결과로 대화 목록이 존재하고, 다음 페이지가 존재할 경우에만 다음 커서 값 지정
		if (hasNext) {
			// 마지막 요소
			ConversationDocument lastConversation = pagedDocuments.get(pagedDocuments.size() - 1);

			nextCursor = lastConversation.getCreatedAt().toString();
			nextIdAfter = lastConversation.getId();
		}

		// 6. 문서에서 ID 목록만 추출
		List<UUID> conversationIds = pagedDocuments.stream()
			.map(ConversationDocument::getId)
			.toList();

		// 7. 실제 대화 목록 조회
		List<Conversation> sortedConversations = fetchAndSortFromRdb(conversationIds);

		// 8. Conversation -> ConversationDto 변환
		List<ConversationDto> data = mapToConversationDtoList(sortedConversations, requestUserId);

		log.debug("[CONVERSATION_FIND_SEARCH] 대화 목록 조회 완료: keyword={}", conversationPageRequest.keywordLike());

		return conversationMapper.toCursorPageResponse(
			data,
			nextCursor,
			nextIdAfter,
			hasNext,
			totalCount,
			conversationPageRequest.sortBy(),
			conversationPageRequest.sortDirection().name()
		);
	}

	// 빈 페이지 응답 객체 반환
	private CursorResponseConversationDto createEmptyPageResponse(
		Long totalCount,
		ConversationPageRequest conversationPageRequest
	) {
		return conversationMapper.toCursorPageResponse(
			Collections.emptyList(),
			null,
			null,
			false,
			totalCount,
			conversationPageRequest.sortBy(),
			conversationPageRequest.sortDirection().name()
		);
	}

	// 실제 대화 목록 조회
	private List<Conversation> fetchAndSortFromRdb(List<UUID> conversationIds) {
		// 1. 대화 목록 조회
		List<Conversation> rdbConversations = conversationRepository.findAllByIdIn(conversationIds);

		// 2. 데이터 정렬을 위한 Map 변환
		Map<UUID, Conversation> conversationMap = rdbConversations.stream()
			.collect(Collectors.toMap(Conversation::getId, conv -> conv));

		// 3. 데이터 정렬
		return conversationIds.stream()
			.map(conversationMap::get)
			.filter(Objects::nonNull)
			.toList();
	}

	// 유효성 검증: 대화 중복 검사
	private void validateDuplicateConversation(UUID requestUserId, UUID withUserId) {
		conversationParticipantRepository.findExistingConversationId(requestUserId, withUserId)
			.ifPresent(conversationId -> {
				throw new ConversationException(ConversationErrorCode.CONVERSATION_ALREADY_EXISTS)
					.addDetail("existingConversationId", conversationId);
			});
	}

	// 유효성 검증: 대화 존재 여부
	private void validateConversation(UUID conversationId) {
		if (ConversationRedisStore.EMPTY_MARKER.equals(conversationId) || conversationId == null) {
			throw new ConversationException(ConversationErrorCode.CONVERSATION_NOT_FOUND)
				.addDetail("conversationId", conversationId);
		}
	}

	// 유효성 검증: 특정 대화방 참가자 여부
	private void validateParticipantsAccess(Set<UUID> participantIds, UUID requestUserId) {
		if (participantIds.isEmpty() || !participantIds.contains(requestUserId)) {
			throw new ConversationException(ConversationErrorCode.CONVERSATION_ACCESS_DENIED)
				.addDetail("requestUserId", requestUserId);
		}
	}

	// 유효성 검증: 자기 자신 조회
	private void validateSelfReadConversation(UUID requestUserId, UUID withUserId) {
		if (requestUserId.equals(withUserId)) {
			throw new ConversationException(ConversationErrorCode.CONVERSATION_SELF_SELECT_MOT_ALLOWED)
				.addDetail("requestUserId", requestUserId)
				.addDetail("withUserId", withUserId);
		}
	}

	// 유효성 검증: 정렬 기준
	private void validateSortField(String sortBy) {
		if (!"createdAt".equals(sortBy)) {
			throw new ConversationException(ConversationErrorCode.CONVERSATION_INVALID_FORMAT)
				.addDetail("sortBy", sortBy);
		}
	}

	// 대화 엔티티 반환
	private Conversation getConversationEntityOrThrow(UUID conversationId) {
		return conversationRepository.findById(conversationId)
			.orElseThrow(() -> new ConversationException(ConversationErrorCode.CONVERSATION_NOT_FOUND)
				.addDetail("conversationId", conversationId));
	}

	// 대화 ID 반환: DB 백필을 위해 Null 반환
	private UUID getConversationIdOrNull(UUID requestUserId, UUID withUserId) {
		return conversationParticipantRepository.findExistingConversationId(requestUserId, withUserId)
			.orElse(null);
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

	// DTO 변환
	private ConversationDto mapToConversationDto(
		Conversation conversation,
		User withUser,
		UUID requestUserId
	) {
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

	// DTO 목록 변환
	private List<ConversationDto> mapToConversationDtoList(
		List<Conversation> conversations,
		UUID requestUserId
	) {
		// 대화 엔티티 목록이 비어있을 경우, 빈 리스트 반환
		if (conversations.isEmpty()) {
			return Collections.emptyList();
		}

		// 대화 엔티티 목록 -> 대화 ID 목록
		List<UUID> conversationIds = conversations.stream()
			.map(Conversation::getId)
			.toList();

		// 다건 조회: 대화 ID 목록에 해당하는 대화 상대 정보, 마지막 메시지, 안 읽음 여부 한 번에 조회
		// 단건 조회와 마찬가지로 대화 목록 크기에 상관없이 총 3번의 쿼리문만 발생하여, N+3 문제 해결
		Map<UUID, User> withUserMap = getWithUserMap(conversationIds, requestUserId);
		Map<UUID, DirectMessage> latestMessageMap = getLatestMessageMap(conversationIds);
		Set<UUID> unreadConversationIds = getUnreadConversationIds(conversationIds, requestUserId);

		return conversations.stream()
			.map(conversation -> {
				UUID conversationId = conversation.getId();

				// 대화 상대 정보 조회
				User withUser = withUserMap.get(conversationId);
				UserSummary withSummary = (withUser != null)
					? getUserSummary(withUser)
					: null;

				// 마지막 메시지 조회
				DirectMessage latestMessage = latestMessageMap.get(conversationId);
				DirectMessageDto latestDto = (latestMessage != null)
					? directMessageMapper.toDto(latestMessage)
					: null;

				// 안 읽음 여부 판단
				boolean hasUnread = unreadConversationIds.contains(conversationId);

				return conversationMapper.toDto(conversation, withSummary, latestDto, hasUnread);
			})
			.toList();
	}

	// 대화 상대 조회: 요청자를 제외한 각 대화방의 상대방 유저 정보를 Map으로 반환
	private Map<UUID, User> getWithUserMap(List<UUID> conversationIds, UUID requestUserId) {
		return conversationParticipantRepository.findByConversationIdIn(conversationIds).stream()
			.filter(participant -> !participant.getUser().getId().equals(requestUserId))
			.collect(
				Collectors.toMap(
					participant -> participant.getConversation().getId(),
					ConversationParticipant::getUser,
					// 참가자가 2명을 초과하는 예외 상황 대비
					(existing, replacement) -> existing)
			);
	}

	// 마지막 메시지 조회: 각 대화방에 속한 가장 최근 메시지를 Map으로 반환
	private Map<UUID, DirectMessage> getLatestMessageMap(List<UUID> conversationIds) {
		return directMessageRepository.findLatestMessagesByConversationIds(conversationIds).stream()
			.collect(
				Collectors.toMap(
					latestMessage -> latestMessage.getConversation().getId(),
					latestMessage -> latestMessage,
					// 두 개 이상의 메시시의 생성 시간이 같은 경우, 기존값을 유지하여 DuplicationKeyException 방지
					(existing, replacement) -> existing
				)
			);
	}

	// 안 읽음 여부 조회: 요청자가 아직 읽지 않은 메시지가 존재하는 대화방 ID 목록을 Set으로 반환
	private Set<UUID> getUnreadConversationIds(List<UUID> conversationIds, UUID receiverId) {
		return directMessageRepository.findUnreadConversationIds(conversationIds, receiverId);
	}
}
