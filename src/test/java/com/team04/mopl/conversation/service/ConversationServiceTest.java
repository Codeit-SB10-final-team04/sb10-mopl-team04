package com.team04.mopl.conversation.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.team04.mopl.common.dto.UserSummary;
import com.team04.mopl.common.enums.SortDirection;
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

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

	@InjectMocks
	private ConversationService conversationService;

	@Mock
	private ConversationRepository conversationRepository;

	@Mock
	private ConversationParticipantRepository conversationParticipantRepository;

	@Mock
	private ConversationElasticSearchRepository conversationElasticSearchRepository;

	@Mock
	private DirectMessageRepository directMessageRepository;

	@Mock
	private UserRepository userRepository;

	@Mock
	private ConversationRedisStore conversationRedisStore;

	@Mock
	private ConversationMapper conversationMapper;

	@Mock
	private ConversationParticipantMapper conversationParticipantMapper;

	@Mock
	private DirectMessageMapper directMessageMapper;

	@Mock
	private ApplicationEventPublisher eventPublisher;

	@Captor
	private ArgumentCaptor<ConversationCreatedEvent> eventCaptor;

	/*
	=========================
	   대화 생성
	=========================
	 */
	@Test
	@DisplayName("성공: 유효한 요청일 경우 대화방을 생성하고 ES 동기화 이벤트를 발행한 뒤 DTO를 반환한다.")
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

		given(conversationParticipantRepository.findExistingConversationId(requestUserId, withUserId))
			.willReturn(Optional.empty());

		ConversationParticipant participant1 = mock(ConversationParticipant.class);
		ConversationParticipant participant2 = mock(ConversationParticipant.class);
		given(participant1.getUser()).willReturn(requestUser);
		given(participant2.getUser()).willReturn(withUser);

		given(conversationParticipantMapper.toEntity(any(Conversation.class), eq(requestUser))).willReturn(
			participant1);
		given(conversationParticipantMapper.toEntity(any(Conversation.class), eq(withUser))).willReturn(participant2);

		given(conversationParticipantRepository.saveAll(anyList())).willReturn(List.of(participant1, participant2));

		ConversationDto expectedDto = mock(ConversationDto.class);
		given(conversationMapper.toDto(any(Conversation.class), any(UserSummary.class), isNull(), eq(false)))
			.willReturn(expectedDto);

		// when
		ConversationDto result = conversationService.createConversation(request, requestUserId);

		// then
		assertThat(result).isNotNull();
		verify(conversationRepository).save(any(Conversation.class));
		verify(conversationParticipantRepository).saveAll(anyList());

		verify(eventPublisher).publishEvent(eventCaptor.capture());
		ConversationCreatedEvent publishedEvent = eventCaptor.getValue();
		assertThat(publishedEvent.participantIds()).containsExactlyInAnyOrder(requestUserId, withUserId);
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

		// 이벤트가 발행되지 않아야 함
		verifyNoInteractions(eventPublisher);
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

		// 이벤트가 발행되지 않아야 함
		verifyNoInteractions(eventPublisher);
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

		UUID withUserId = UUID.randomUUID();
		User withUser = mock(User.class);
		given(withUser.getId()).willReturn(withUserId);

		UUID conversationId = UUID.randomUUID();
		Conversation conversation = mock(Conversation.class);
		given(conversation.getId()).willReturn(conversationId);

		DirectMessage latestMessage = mock(DirectMessage.class);
		DirectMessageDto latestMessageDto = mock(DirectMessageDto.class);

		given(conversationRepository.findById(conversationId)).willReturn(Optional.of(conversation));

		// Redis 캐시 Hit 가정
		given(conversationRedisStore.getParticipants(conversationId)).willReturn(Set.of(requestUserId, withUserId));
		given(userRepository.findById(withUserId)).willReturn(Optional.of(withUser));

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

		UUID withUserId = UUID.randomUUID();
		User withUser = mock(User.class);
		given(withUser.getId()).willReturn(withUserId);

		UUID conversationId = UUID.randomUUID();
		Conversation conversation = mock(Conversation.class);
		given(conversation.getId()).willReturn(conversationId);

		given(conversationRepository.findById(conversationId)).willReturn(Optional.of(conversation));

		// Redis 캐시 Hit 가정
		given(conversationRedisStore.getParticipants(conversationId)).willReturn(Set.of(requestUserId, withUserId));
		given(userRepository.findById(withUserId)).willReturn(Optional.of(withUser));

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
	@DisplayName("실패: 대화 참여자 목록에 상대방 유저가 존재하지 않으면 ConversationException이 발생한다.")
	void findConversationById_WithUserNotFound_ThrowException() {
		// given
		UUID requestUserId = UUID.randomUUID();

		UUID conversationId = UUID.randomUUID();
		Conversation conversation = mock(Conversation.class);
		given(conversation.getId()).willReturn(conversationId);

		given(conversationRepository.findById(conversationId)).willReturn(Optional.of(conversation));

		// 대화 상대 미존재 (요청자 본인만 캐시에서 반환될 경우)
		given(conversationRedisStore.getParticipants(conversationId)).willReturn(Set.of(requestUserId));

		// when & then
		assertThatThrownBy(() -> conversationService.findConversationById(conversationId, requestUserId))
			.isInstanceOf(ConversationException.class);
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

		// Redis 캐시 Hit
		given(conversationRedisStore.getConversationId(requestUserId, withUserId))
			.willReturn(conversationId);
		given(conversationRepository.findById(conversationId)).willReturn(Optional.of(conversation));

		ConversationDto mockDto = mock(ConversationDto.class);
		given(conversationMapper.toDto(any(), any(), any(), anyBoolean())).willReturn(mockDto);

		// when
		ConversationDto result = conversationService.findConversationByUserId(withUserId, requestUserId);

		// then
		assertThat(result).isNotNull();
		assertThat(result).isSameAs(mockDto);
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

		// Cache Miss
		given(conversationRedisStore.getConversationId(requestUserId, withUserId))
			.willReturn(null);
		given(conversationParticipantRepository.findExistingConversationId(requestUserId, withUserId))
			.willReturn(Optional.empty());

		// when & then
		assertThatThrownBy(() -> conversationService.findConversationByUserId(withUserId, requestUserId))
			.isInstanceOf(ConversationException.class)
			.hasFieldOrPropertyWithValue("errorCode", ConversationErrorCode.CONVERSATION_NOT_FOUND);

		// DB 백필 호출 여부 확인 (null 저장 시 빈 방 마커가 세팅됨)
		verify(conversationRedisStore).initConversationMapping(requestUserId, withUserId, null);
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

		UUID conv1Id = UUID.randomUUID();
		ConversationDocument doc1 = mock(ConversationDocument.class);
		given(doc1.getId()).willReturn(conv1Id);

		UUID conv2Id = UUID.randomUUID();
		ConversationDocument doc2 = mock(ConversationDocument.class);
		given(doc2.getId()).willReturn(conv2Id);
		String mockCursorTime = Instant.now().toString();
		given(doc2.getCreatedAt()).willReturn(Instant.parse(mockCursorTime));

		UUID conv3Id = UUID.randomUUID();
		ConversationDocument doc3 = mock(ConversationDocument.class);

		List<ConversationDocument> esDocuments = List.of(doc1, doc2, doc3);

		Conversation conv1 = mock(Conversation.class);
		given(conv1.getId()).willReturn(conv1Id);
		Conversation conv2 = mock(Conversation.class);
		given(conv2.getId()).willReturn(conv2Id);

		// ES
		given(conversationElasticSearchRepository.searchConversation(request, requestUserId))
			.willReturn(esDocuments);
		given(conversationElasticSearchRepository.countConversation(request, requestUserId))
			.willReturn(3L);

		given(conversationRepository.findAllByIdIn(anyList())).willReturn(List.of(conv1, conv2));

		// N+3 방어 로직
		given(conversationParticipantRepository.findByConversationIdIn(anyList())).willReturn(List.of());
		given(directMessageRepository.findLatestMessagesByConversationIds(anyList())).willReturn(List.of());
		given(directMessageRepository.findUnreadConversationIds(anyList(), eq(requestUserId)))
			.willReturn(Set.of());

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
		verify(conversationRepository).findAllByIdIn(List.of(conv1Id, conv2Id));
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

		given(conversationElasticSearchRepository.searchConversation(request, requestUserId)).willReturn(List.of());
		given(conversationElasticSearchRepository.countConversation(request, requestUserId)).willReturn(0L);

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
	@DisplayName("성공: RDB에서 조회된 데이터 순서가 뒤죽박죽이어도 ES 문서 순서에 맞게 재정렬된다.")
	void findAll_OrderMaintained_Success() {
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

		UUID id1 = UUID.randomUUID();
		ConversationDocument doc1 = mock(ConversationDocument.class);
		given(doc1.getId()).willReturn(id1);

		UUID id2 = UUID.randomUUID();
		ConversationDocument doc2 = mock(ConversationDocument.class);
		given(doc2.getId()).willReturn(id2);

		UUID id3 = UUID.randomUUID();
		ConversationDocument doc3 = mock(ConversationDocument.class);
		given(doc3.getId()).willReturn(id3);

		given(conversationElasticSearchRepository.searchConversation(request, requestUserId))
			.willReturn(List.of(doc1, doc2, doc3));
		given(conversationElasticSearchRepository.countConversation(request, requestUserId))
			.willReturn(3L);

		// RDB에서는 순서가 무작위로 조회된다고 세팅
		Conversation conv1 = mock(Conversation.class);
		given(conv1.getId()).willReturn(id1);
		Conversation conv2 = mock(Conversation.class);
		given(conv2.getId()).willReturn(id2);
		Conversation conv3 = mock(Conversation.class);
		given(conv3.getId()).willReturn(id3);

		given(conversationRepository.findAllByIdIn(anyList()))
			.willReturn(List.of(conv3, conv1, conv2));

		given(conversationParticipantRepository.findByConversationIdIn(anyList())).willReturn(List.of());
		given(directMessageRepository.findLatestMessagesByConversationIds(anyList())).willReturn(List.of());
		given(directMessageRepository.findUnreadConversationIds(anyList(), eq(requestUserId))).willReturn(Set.of());

		// when
		conversationService.findAll(request, requestUserId);

		// then
		InOrder inOrder = inOrder(conversationMapper);
		inOrder.verify(conversationMapper).toDto(eq(conv1), any(), any(), anyBoolean());
		inOrder.verify(conversationMapper).toDto(eq(conv2), any(), any(), anyBoolean());
		inOrder.verify(conversationMapper).toDto(eq(conv3), any(), any(), anyBoolean());
	}

	@Test
	@DisplayName("실패: sortBy가 createdAt이 아니면 예외가 발생하고 ES 쿼리는 실행되지 않는다.")
	void findAll_InvalidSortBy_Fail() {
		// given
		UUID requestUserId = UUID.randomUUID();
		ConversationPageRequest request = new ConversationPageRequest(
			null,
			null,
			null,
			10,
			SortDirection.DESCENDING,
			"invalidSort"
		);

		// when & then
		assertThatThrownBy(() -> conversationService.findAll(request, requestUserId))
			.isInstanceOf(ConversationException.class)
			.hasMessageContaining(ConversationErrorCode.CONVERSATION_INVALID_FORMAT.getMessage());

		verifyNoInteractions(conversationElasticSearchRepository);
	}
}