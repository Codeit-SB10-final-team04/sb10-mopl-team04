package com.team04.mopl.conversation.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.team04.mopl.common.dto.UserSummary;
import com.team04.mopl.common.enums.SortDirection;
import com.team04.mopl.conversation.dto.request.ConversationCreateRequest;
import com.team04.mopl.conversation.dto.request.ConversationPageRequest;
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

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

	@InjectMocks
	private ConversationService conversationService;

	@Mock
	private ConversationRepository conversationRepository;

	@Mock
	private ConversationParticipantRepository conversationParticipantRepository;

	@Mock
	private DirectMessageRepository directMessageRepository;

	@Mock
	private UserRepository userRepository;

	@Mock
	private ConversationMapper conversationMapper;

	@Mock
	private ConversationParticipantMapper conversationParticipantMapper;

	@Mock
	private DirectMessageMapper directMessageMapper;

	/*
	=========================
	   대화 생성
	=========================
	 */
	@Test
	@DisplayName("성공: 유효한 요청일 경우 대화방을 생성하고 DTO를 반환한다.")
	void createConversation_Success() {
		// given
		UUID requestUserId = UUID.randomUUID();
		User requestUser = mock(User.class);
		given(requestUser.getId()).willReturn(requestUserId);

		UUID withUserId = UUID.randomUUID();
		ConversationCreateRequest request = new ConversationCreateRequest(withUserId);
		User withUser = mock(User.class);
		given(withUser.getId()).willReturn(withUserId);

		given(userRepository.findById(requestUserId)).willReturn(Optional.of(requestUser));
		given(userRepository.findById(withUserId)).willReturn(Optional.of(withUser));

		// 요청자와 대화 참여자 간의 대화방 미존재
		given(conversationParticipantRepository.findExistingConversationId(requestUserId, withUserId))
			.willReturn(Optional.empty());

		// 대화 참여자 생성
		given(conversationParticipantMapper.toEntity(any(Conversation.class), any(User.class)))
			.willReturn(mock(ConversationParticipant.class));

		ConversationDto expectedDto = mock(ConversationDto.class);
		given(conversationMapper.toDto(any(Conversation.class), any(UserSummary.class), isNull(), eq(false)))
			.willReturn(expectedDto);

		// when
		ConversationDto result = conversationService.createConversation(request, requestUserId);

		// then
		assertThat(result).isNotNull();
		verify(conversationRepository).save(any(Conversation.class));
		verify(conversationParticipantRepository).saveAll(anyList());
	}

	@Test
	@DisplayName("실패: 대상 사용자가 존재하지 않으면 예외가 발생한다.")
	void createConversation_UserNotFound_Fail() {
		// given
		UUID requestUserId = UUID.randomUUID();
		User requestUser = mock(User.class);

		UUID withUserId = UUID.randomUUID();
		ConversationCreateRequest request = new ConversationCreateRequest(withUserId);

		given(userRepository.findById(requestUserId)).willReturn(Optional.of(requestUser));
		// 대화 참여자 미존재
		given(userRepository.findById(withUserId)).willReturn(Optional.empty());

		// when & then
		assertThatThrownBy(() -> conversationService.createConversation(request, requestUserId))
			.isInstanceOf(UserException.class)
			.hasMessageContaining(UserErrorCode.USER_NOT_FOUND.getMessage());
	}

	@Test
	@DisplayName("실패: 이미 존재하는 대화방일 경우 중복 예외가 발생한다.")
	void createConversation_DuplicateConversation_Fail() {
		// given
		UUID requestUserId = UUID.randomUUID();
		User requestUser = mock(User.class);
		given(requestUser.getId()).willReturn(requestUserId);

		UUID withUserId = UUID.randomUUID();
		ConversationCreateRequest request = new ConversationCreateRequest(withUserId);
		User withUser = mock(User.class);
		given(withUser.getId()).willReturn(withUserId);

		UUID existingConversationId = UUID.randomUUID();

		given(userRepository.findById(requestUserId)).willReturn(Optional.of(requestUser));
		given(userRepository.findById(withUserId)).willReturn(Optional.of(withUser));

		// 요청자와 대화 참여자 간의 대화방 존재
		given(conversationParticipantRepository.findExistingConversationId(requestUserId, withUserId))
			.willReturn(Optional.of(existingConversationId));

		// when & then
		assertThatThrownBy(() -> conversationService.createConversation(request, requestUserId))
			.isInstanceOf(ConversationException.class)
			.hasMessage(ConversationErrorCode.CONVERSATION_ALREADY_EXISTS.getMessage());
	}

	@Test
	@DisplayName("실패: 저장 시 동시성 이슈(DB 제약조건 위반) 발생 시 중복 예외로 변환하여 발생시킨다.")
	void createConversation_ConcurrencyIssue_Fail() {
		// given
		UUID requestUserId = UUID.randomUUID();
		User requestUser = mock(User.class);
		given(requestUser.getId()).willReturn(requestUserId);

		UUID withUserId = UUID.randomUUID();
		ConversationCreateRequest request = new ConversationCreateRequest(withUserId);
		User withUser = mock(User.class);
		given(withUser.getId()).willReturn(withUserId);

		given(userRepository.findById(requestUserId)).willReturn(Optional.of(requestUser));
		given(userRepository.findById(withUserId)).willReturn(Optional.of(withUser));

		given(conversationParticipantRepository.findExistingConversationId(requestUserId, withUserId))
			.willReturn(Optional.empty());

		// 중복 요청으로 인한 DataIntegrityViolationException 발생
		willThrow(DataIntegrityViolationException.class)
			.given(conversationRepository).save(any(Conversation.class));

		// when & then
		assertThatThrownBy(() -> conversationService.createConversation(request, requestUserId))
			.isInstanceOf(ConversationException.class)
			.hasMessage(ConversationErrorCode.CONVERSATION_ALREADY_EXISTS.getMessage());
	}

	/*
	=========================
	   대화 단건 조회
	=========================
	 */
	@Test
	@DisplayName("성공: 메시지가 존재하는 대화방을 조회하면 상대방 정보와 마지막 메시지를 조립하여 반환한다.")
	void findConversationById_WithLatestMessage_Success() {
		// given
		UUID requestUserId = UUID.randomUUID();
		User requestUser = mock(User.class);
		given(requestUser.getId()).willReturn(requestUserId);

		UUID withUserId = UUID.randomUUID();
		User withUser = mock(User.class);
		given(withUser.getId()).willReturn(withUserId);

		UUID conversationId = UUID.randomUUID();
		Conversation conversation = mock(Conversation.class);
		given(conversation.getId()).willReturn(conversationId);

		ConversationParticipant participant1 = mock(ConversationParticipant.class);
		ConversationParticipant participant2 = mock(ConversationParticipant.class);

		DirectMessage latestMessage = mock(DirectMessage.class);
		DirectMessageDto latestMessageDto = mock(DirectMessageDto.class);

		given(conversationRepository.findById(conversationId)).willReturn(Optional.of(conversation));
		given(conversationParticipantRepository.findByConversationId(conversationId)).willReturn(
			List.of(participant1, participant2));
		given(participant1.getUser()).willReturn(requestUser);
		given(participant2.getUser()).willReturn(withUser);

		given(directMessageRepository.findTopByConversationIdOrderByCreatedAtDescIdDesc(conversationId)).willReturn(
			Optional.of(latestMessage));
		given(directMessageMapper.toDto(latestMessage)).willReturn(latestMessageDto);

		ConversationDto expectedDto = mock(ConversationDto.class);
		given(conversationMapper.toDto(eq(conversation), any(UserSummary.class), eq(latestMessageDto), eq(false)))
			.willReturn(expectedDto);

		// when
		ConversationDto result = conversationService.findConversationById(conversationId, requestUserId);

		// then
		assertThat(result).isEqualTo(expectedDto);
		verify(conversationMapper).toDto(eq(conversation), any(UserSummary.class), eq(latestMessageDto), eq(false));
	}

	@Test
	@DisplayName("성공: 메시지가 없는 신규 대화방을 조회하면 마지막 메시지는 null, 안읽음 여부는 false로 반환한다.")
	void findConversationById_NoMessage_Success() {
		// given
		UUID requestUserId = UUID.randomUUID();
		User requestUser = mock(User.class);
		given(requestUser.getId()).willReturn(requestUserId);

		UUID withUserId = UUID.randomUUID();
		User withUser = mock(User.class);
		given(withUser.getId()).willReturn(withUserId);

		UUID conversationId = UUID.randomUUID();
		Conversation conversation = mock(Conversation.class);
		given(conversation.getId()).willReturn(conversationId);

		ConversationParticipant participant1 = mock(ConversationParticipant.class);
		ConversationParticipant participant2 = mock(ConversationParticipant.class);

		given(conversationRepository.findById(conversationId)).willReturn(Optional.of(conversation));
		given(conversationParticipantRepository.findByConversationId(conversationId)).willReturn(
			List.of(participant1, participant2));
		given(participant1.getUser()).willReturn(requestUser);
		given(participant2.getUser()).willReturn(withUser);

		// 마지막 메시지 없음
		given(directMessageRepository.findTopByConversationIdOrderByCreatedAtDescIdDesc(conversationId)).willReturn(
			Optional.empty());

		ConversationDto expectedDto = mock(ConversationDto.class);
		given(conversationMapper.toDto(eq(conversation), any(UserSummary.class), eq(null), eq(false)))
			.willReturn(expectedDto);

		// when
		ConversationDto result = conversationService.findConversationById(conversationId, requestUserId);

		// then
		assertThat(result).isEqualTo(expectedDto);
		verifyNoInteractions(directMessageMapper);
	}

	@Test
	@DisplayName("실패: 존재하지 않는 대화방 ID로 조회하면 ConversationException이 발생한다.")
	void findConversationById_ConversationNotFound_ThrowException() {
		// given
		UUID requestUserId = UUID.randomUUID();
		UUID conversationId = UUID.randomUUID();

		given(conversationRepository.findById(conversationId)).willReturn(Optional.empty());

		// when & then
		assertThatThrownBy(() -> conversationService.findConversationById(conversationId, requestUserId))
			.isInstanceOf(ConversationException.class);
	}

	@Test
	@DisplayName("실패: 대화 참여자 목록에 상대방 유저가 존재하지 않으면 UserException이 발생한다.")
	void findConversationById_WithUserNotFound_ThrowException() {
		// given
		UUID requestUserId = UUID.randomUUID();
		User requestUser = mock(User.class);
		given(requestUser.getId()).willReturn(requestUserId);

		UUID conversationId = UUID.randomUUID();
		Conversation conversation = mock(Conversation.class);
		given(conversation.getId()).willReturn(conversationId);

		ConversationParticipant participant1 = mock(ConversationParticipant.class);

		given(conversationRepository.findById(conversationId)).willReturn(Optional.of(conversation));
		// 대화 상대 미존재
		given(conversationParticipantRepository.findByConversationId(conversationId)).willReturn(List.of(participant1));
		given(participant1.getUser()).willReturn(requestUser);

		// when & then
		assertThatThrownBy(() -> conversationService.findConversationById(conversationId, requestUserId))
			.isInstanceOf(UserException.class);
	}

	/*
	=========================
	   특정 사용자와의 대화 조회
	=========================
	 */
	@Test
	@DisplayName("성공: 특정 사용자 조회 시 기존 대화방이 존재하면 DTO를 반환한다")
	void findConversationByUserId_Success_Existing() {
		// given
		UUID requestUserId = UUID.randomUUID();

		UUID withUserId = UUID.randomUUID();
		User withUser = mock(User.class);
		given(withUser.getId()).willReturn(withUserId);
		given(userRepository.findById(withUserId)).willReturn(Optional.of(withUser));

		UUID conversationId = UUID.randomUUID();
		Conversation conversation = mock(Conversation.class);
		given(conversation.getId()).willReturn(conversationId);

		given(conversationParticipantRepository.findExistingConversationId(requestUserId, withUserId))
			.willReturn(Optional.of(conversationId));
		given(conversationRepository.findById(conversationId)).willReturn(Optional.of(conversation));

		ConversationDto mockDto = mock(ConversationDto.class);
		given(conversationMapper.toDto(any(), any(), any(), anyBoolean())).willReturn(mockDto);

		// when
		ConversationDto result = conversationService.findConversationByUserId(withUserId, requestUserId);

		// then
		assertThat(result).isNotNull();
		assertThat(result).isSameAs(mockDto);
		verify(conversationParticipantRepository).findExistingConversationId(requestUserId, withUserId);
		verify(conversationRepository).findById(conversationId);
	}

	@Test
	@DisplayName("실패: 존재하지 않는 userId로 조회 시 UserException 발생")
	void findConversationByUserId_Fail_UserNotFound() {
		// given
		UUID requestUserId = UUID.randomUUID();
		UUID userId = UUID.randomUUID();

		given(userRepository.findById(userId)).willReturn(Optional.empty());

		// when & then
		assertThatThrownBy(() -> conversationService.findConversationByUserId(userId, requestUserId))
			.isInstanceOf(UserException.class);
	}

	@Test
	@DisplayName("실패: 상대 유저는 존재하지만, 공통 대화방이 없으면 ConversationException이 발생한다")
	void findConversationByUserId_Fail_ConversationNotFound() {
		// given
		UUID requestUserId = UUID.randomUUID();

		UUID withUserId = UUID.randomUUID();
		User withUser = mock(User.class);
		given(withUser.getId()).willReturn(withUserId);

		given(userRepository.findById(withUserId)).willReturn(Optional.of(withUser));

		given(conversationParticipantRepository.findExistingConversationId(requestUserId, withUserId))
			.willReturn(Optional.empty());

		// when & then
		assertThatThrownBy(() -> conversationService.findConversationByUserId(withUserId, requestUserId))
			.isInstanceOf(ConversationException.class)
			.hasFieldOrPropertyWithValue("errorCode", ConversationErrorCode.CONVERSATION_NOT_FOUND);
	}

	/*
	=========================
	   대화 목록 조회
	=========================
	 */
	@Test
	@DisplayName("성공: 조건에 맞는 대화 목록을 N+3 최적화하여 반환한다.")
	void findAll_Success_HasNext() {
		// given
		UUID requestUserId = UUID.randomUUID();
		ConversationPageRequest request = new ConversationPageRequest(
			null,
			null,
			null,
			2,
			SortDirection.DESCENDING,
			"createdAt"
		);

		Conversation conv1 = mock(Conversation.class);
		Conversation conv2 = mock(Conversation.class);
		// 다음 페이지에 존재하는 대화
		Conversation conv3 = mock(Conversation.class);

		UUID conv1Id = UUID.randomUUID();
		given(conv1.getId()).willReturn(conv1Id);
		UUID conv2Id = UUID.randomUUID();
		given(conv2.getId()).willReturn(conv2Id);

		// 마지막 요소로부터 커서 추출
		String mockCursorTime = java.time.Instant.now().toString();
		given(conv2.getCreatedAt()).willReturn(java.time.Instant.parse(mockCursorTime));

		List<Conversation> pagedConversations = List.of(conv1, conv2, conv3);

		given(conversationRepository.searchConversation(request, requestUserId)).willReturn(pagedConversations);
		given(conversationRepository.countConversation(request, requestUserId)).willReturn(3L);

		// N+3 방어 로직
		given(conversationParticipantRepository.findByConversationIdIn(anyList())).willReturn(List.of());
		given(directMessageRepository.findLatestMessagesByConversationIds(anyList())).willReturn(List.of());
		given(directMessageRepository.findUnreadConversationIds(anyList(), eq(requestUserId))).willReturn(
			java.util.Set.of());

		// 빌더 패턴 적용
		CursorResponseConversationDto expectedResponse = CursorResponseConversationDto.builder()
			.data(List.of(mock(ConversationDto.class), mock(ConversationDto.class)))
			.nextCursor(mockCursorTime) // conv2의 생성 시간
			.nextIdAfter(conv2Id)       // conv2의 ID
			.hasNext(true)
			.totalCount(3L)
			.sortBy("createdAt")
			.sortDirection("DESCENDING")
			.build();

		given(conversationMapper.toCursorPageResponse(anyList(), anyString(), any(UUID.class), eq(true), eq(3L), any(),
			any()))
			.willReturn(expectedResponse);

		// when
		CursorResponseConversationDto result = conversationService.findAll(request, requestUserId);

		// then
		assertThat(result).isNotNull();
		assertThat(result).isEqualTo(expectedResponse);
		verify(conversationMapper).toCursorPageResponse(anyList(), anyString(), any(UUID.class), eq(true), eq(3L),
			any(), any());
	}

	@Test
	@DisplayName("성공: 조회된 대화 목록이 빈 리스트일 경우 정상적으로 빈 커서 응답을 반환한다.")
	void findAll_Success_EmptyList() {
		// given
		UUID requestUserId = UUID.randomUUID();
		ConversationPageRequest request = new ConversationPageRequest(
			null,
			null,
			null,
			10,
			SortDirection.DESCENDING,
			"createdAt"
		);

		given(conversationRepository.searchConversation(request, requestUserId)).willReturn(List.of());
		given(conversationRepository.countConversation(request, requestUserId)).willReturn(0L);

		// 빌더 패턴 적용
		CursorResponseConversationDto expectedResponse = CursorResponseConversationDto.builder()
			.data(Collections.emptyList())
			.nextCursor(null)
			.nextIdAfter(null)
			.hasNext(false)
			.totalCount(0L)
			.sortBy("createdAt")
			.sortDirection("DESCENDING")
			.build();

		given(conversationMapper.toCursorPageResponse(anyList(), isNull(), isNull(), eq(false), eq(0L), any(), any()))
			.willReturn(expectedResponse);

		// when
		CursorResponseConversationDto result = conversationService.findAll(request, requestUserId);

		// then
		assertThat(result).isNotNull();
		assertThat(result).isEqualTo(expectedResponse);
		verify(conversationParticipantRepository, never()).findByConversationIdIn(anyList());
		verify(directMessageRepository, never()).findLatestMessagesByConversationIds(anyList());
	}

	@Test
	@DisplayName("실패: Repository 조회 중 파라미터(cursor/sortBy) 검증 실패로 인한 예외 발생 시 Service도 예외를 던진다.")
	void findAll_Fail_RepositoryThrowsException() {
		// given
		UUID requestUserId = UUID.randomUUID();
		ConversationPageRequest request = mock(ConversationPageRequest.class);

		// QDSL 예외 발생
		given(conversationRepository.searchConversation(request, requestUserId))
			.willThrow(new ConversationException(ConversationErrorCode.CONVERSATION_INVALID_FORMAT));

		// when & then
		assertThatThrownBy(() -> conversationService.findAll(request, requestUserId))
			.isInstanceOf(ConversationException.class)
			.hasMessageContaining(ConversationErrorCode.CONVERSATION_INVALID_FORMAT.getMessage());

		verifyNoInteractions(conversationParticipantRepository);
		verifyNoInteractions(directMessageRepository);
	}
}