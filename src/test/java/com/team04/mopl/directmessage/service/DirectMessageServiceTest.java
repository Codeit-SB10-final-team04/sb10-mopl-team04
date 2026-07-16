package com.team04.mopl.directmessage.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.team04.mopl.common.enums.SortDirection;
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
import com.team04.mopl.directmessage.exception.DirectMessageErrorCode;
import com.team04.mopl.directmessage.exception.DirectMessageException;
import com.team04.mopl.directmessage.mapper.DirectMessageMapper;
import com.team04.mopl.directmessage.redis.DirectMessageRedisStore;
import com.team04.mopl.directmessage.repository.DirectMessageRepository;
import com.team04.mopl.user.entity.User;

@ExtendWith(MockitoExtension.class)
class DirectMessageServiceTest {

	@Mock
	private DirectMessageRepository directMessageRepository;

	@Mock
	private ConversationRepository conversationRepository;

	@Mock
	private ConversationParticipantRepository conversationParticipantRepository;

	@Mock
	private DirectMessageMapper directMessageMapper;

	@Mock
	private DirectMessageRedisStore directMessageRedisStore;

	@Mock
	private ApplicationEventPublisher eventPublisher;

	@InjectMocks
	private DirectMessageService directMessageService;

	/*
	=========================
	   DM 생성 (전송)
	=========================
	 */
	@Test
	@DisplayName("성공: 조건이 모두 맞으면 DM을 생성하고 저장한 후 DTO를 반환한다.")
	void create_Success() {
		// given
		UUID conversationId = UUID.randomUUID();
		Conversation conversation = mock(Conversation.class);

		UUID senderId = UUID.randomUUID();
		User sender = mock(User.class);
		given(sender.getId()).willReturn(senderId);

		ConversationParticipant senderParticipant = mock(ConversationParticipant.class);
		given(senderParticipant.getUser()).willReturn(sender);

		UUID receiverId = UUID.randomUUID();
		User receiver = mock(User.class);
		given(receiver.getId()).willReturn(receiverId);

		ConversationParticipant receiverParticipant = mock(ConversationParticipant.class);
		given(receiverParticipant.getUser()).willReturn(receiver);

		DirectMessageSendRequest request = new DirectMessageSendRequest("안녕하세요!");

		UUID directMessageId = UUID.randomUUID();
		DirectMessage directMessage = mock(DirectMessage.class);
		given(directMessage.getId()).willReturn(directMessageId);

		DirectMessageDto expectedDto = mock(DirectMessageDto.class);

		given(conversationRepository.findById(conversationId)).willReturn(Optional.of(conversation));
		given(conversationParticipantRepository.findByConversationId(conversationId))
			.willReturn(List.of(senderParticipant, receiverParticipant));
		given(directMessageMapper.toEntity(conversation, sender, receiver, request)).willReturn(directMessage);
		given(directMessageMapper.toDto(directMessage)).willReturn(expectedDto);

		// when
		DirectMessageDto result = directMessageService.create(conversationId, request, senderId);

		ArgumentCaptor<DirectMessageCreatedEvent> captor = ArgumentCaptor.forClass(DirectMessageCreatedEvent.class);

		verify(eventPublisher, atLeastOnce()).publishEvent(captor.capture());

		DirectMessageCreatedEvent published = captor.getAllValues().stream()
			.filter(e -> e instanceof DirectMessageCreatedEvent)
			.map(e -> (DirectMessageCreatedEvent)e)
			.findFirst()
			.orElseThrow();

		// then
		assertThat(result).isEqualTo(expectedDto);
		assertThat(published.receiverId()).isEqualTo(receiverId);
		assertThat(published.directMessageId()).isEqualTo(directMessageId);
		assertThat(published.directMessageDto()).isEqualTo(expectedDto);

		verify(directMessageRepository).save(directMessage);
		verify(directMessageRedisStore, never()).addDirectMessage(any(), any(), any());
	}

	@Test
	@DisplayName("실패: 대화방이 존재하지 않으면 DM 생성 시 예외가 발생한다.")
	void create_Fail_ConversationNotFound() {
		// given
		UUID conversationId = UUID.randomUUID();
		UUID senderId = UUID.randomUUID();
		DirectMessageSendRequest request = new DirectMessageSendRequest("안녕하세요!");

		given(conversationRepository.findById(conversationId)).willReturn(Optional.empty());

		// when & then
		assertThatThrownBy(() -> directMessageService.create(conversationId, request, senderId))
			.isInstanceOf(ConversationException.class)
			.hasMessageContaining(ConversationErrorCode.CONVERSATION_NOT_FOUND.getMessage());
	}

	@Test
	@DisplayName("실패: 송신자가 해당 대화방의 참여자가 아니면 예외가 발생한다.")
	void create_Fail_SenderNotInParticipants() {
		// given
		UUID conversationId = UUID.randomUUID();
		Conversation conversation = mock(Conversation.class);
		given(conversationRepository.findById(conversationId)).willReturn(Optional.of(conversation));

		UUID senderId = UUID.randomUUID();

		UUID receiverId = UUID.randomUUID();
		User receiver = mock(User.class);
		given(receiver.getId()).willReturn(receiverId);

		ConversationParticipant receiverParticipant = mock(ConversationParticipant.class);
		given(receiverParticipant.getUser()).willReturn(receiver);

		DirectMessageSendRequest request = new DirectMessageSendRequest("안녕하세요!");

		// 참여자 목록에 송신자가 없는 경우
		given(conversationParticipantRepository.findByConversationId(conversationId))
			.willReturn(List.of(receiverParticipant));

		// when & then
		assertThatThrownBy(() -> directMessageService.create(conversationId, request, senderId))
			.isInstanceOf(ConversationException.class)
			.hasMessageContaining(ConversationErrorCode.CONVERSATION_ACCESS_DENIED.getMessage());
	}

	@Test
	@DisplayName("실패: 수신자가 존재하지 않으면(나홀로 방인 경우) 예외가 발생한다.")
	void create_Fail_ReceiverNotInParticipants() {
		// given
		UUID conversationId = UUID.randomUUID();
		Conversation conversation = mock(Conversation.class);
		given(conversationRepository.findById(conversationId)).willReturn(Optional.of(conversation));

		UUID senderId = UUID.randomUUID();
		User sender = mock(User.class);
		given(sender.getId()).willReturn(senderId);
		ConversationParticipant senderParticipant = mock(ConversationParticipant.class);
		given(senderParticipant.getUser()).willReturn(sender);

		DirectMessageSendRequest request = new DirectMessageSendRequest("안녕하세요!");

		// 참여자 목록에 송신자만 존재하는 경우
		given(conversationParticipantRepository.findByConversationId(conversationId))
			.willReturn(List.of(senderParticipant));

		// when & then
		assertThatThrownBy(() -> directMessageService.create(conversationId, request, senderId))
			.isInstanceOf(ConversationException.class)
			.hasMessageContaining(ConversationErrorCode.CONVERSATION_PARTICIPANT_NOT_FOUND.getMessage());
	}

	/*
	=========================
	   DM 읽음 처리 생성
	=========================
	 */
	@Test
	@DisplayName("성공: 조건이 모두 맞으면 DM 읽음 처리가 정상 수행되고 읽음 이벤트를 발행한다.")
	void markAsRead_Success() {
		// given
		UUID conversationId = UUID.randomUUID();
		Conversation conversation = mock(Conversation.class);
		given(conversation.getId()).willReturn(conversationId);

		UUID requestUserId = UUID.randomUUID();
		User receiver = mock(User.class);
		given(receiver.getId()).willReturn(requestUserId);

		UUID directMessageId = UUID.randomUUID();
		DirectMessage directMessage = mock(DirectMessage.class);
		given(directMessage.getConversation()).willReturn(conversation);
		given(directMessage.getReceiver()).willReturn(receiver);

		given(conversationRepository.findById(conversationId)).willReturn(Optional.of(conversation));
		given(directMessageRepository.findById(directMessageId)).willReturn(Optional.of(directMessage));

		ConversationParticipant participant = mock(ConversationParticipant.class);
		given(participant.getUser()).willReturn(receiver);
		given(conversationParticipantRepository.findByConversationId(conversationId))
			.willReturn(List.of(participant));

		// when
		directMessageService.markAsRead(conversationId, directMessageId, requestUserId);

		// then
		verify(directMessage).markAsRead();

		ArgumentCaptor<DirectMessageReadEvent> captor = ArgumentCaptor.forClass(DirectMessageReadEvent.class);
		verify(eventPublisher).publishEvent(captor.capture());

		DirectMessageReadEvent event = captor.getValue();
		assertThat(event.receiverId()).isEqualTo(requestUserId);
		assertThat(event.conversationId()).isEqualTo(conversationId);
		assertThat(event.directMessageId()).isEqualTo(directMessageId);
	}

	@Test
	@DisplayName("실패: 대화방이 존재하지 않으면 예외가 발생한다")
	void markAsRead_Fail_ConversationNotFound() {
		// given
		UUID conversationId = UUID.randomUUID();
		given(conversationRepository.findById(conversationId)).willReturn(Optional.empty());

		// when & then
		assertThatThrownBy(() -> directMessageService.markAsRead(conversationId, UUID.randomUUID(), UUID.randomUUID()))
			.isInstanceOf(ConversationException.class);
	}

	@Test
	@DisplayName("실패: 대화방에 다른 참여자는 있지만 요청자가 그 목록에 없으면 예외가 발생한다")
	void markAsRead_Fail_NotParticipant() {
		// given
		UUID requestUserId = UUID.randomUUID();

		// 요청자가 아닌 다른 참여자
		UUID otherUserId = UUID.randomUUID();
		ConversationParticipant otherParticipant = mock(ConversationParticipant.class);
		User otherUser = mock(User.class);
		given(otherUser.getId()).willReturn(otherUserId);
		given(otherParticipant.getUser()).willReturn(otherUser);

		UUID conversationId = UUID.randomUUID();
		Conversation conversation = mock(Conversation.class);
		given(conversation.getId()).willReturn(conversationId);

		given(conversationRepository.findById(conversationId)).willReturn(Optional.of(conversation));

		// 다른 참여자 리스트 반환
		given(conversationParticipantRepository.findByConversationId(conversationId))
			.willReturn(List.of(otherParticipant));

		// when & then
		org.assertj.core.api.Assertions.assertThatThrownBy(() ->
				directMessageService.markAsRead(conversationId, UUID.randomUUID(), requestUserId))
			.isInstanceOf(ConversationException.class); // 접근 권한 예외 발생 기대
	}

	@Test
	@DisplayName("실패: DM이 존재하지 않으면 예외가 발생한다")
	void markAsRead_Fail_DmNotFound() {
		// given
		UUID requestUserId = UUID.randomUUID();
		User user = mock(User.class);
		given(user.getId()).willReturn(requestUserId);

		ConversationParticipant participant = mock(ConversationParticipant.class);
		given(participant.getUser()).willReturn(user);

		UUID conversationId = UUID.randomUUID();
		Conversation conversation = mock(Conversation.class);
		given(conversation.getId()).willReturn(conversationId);

		UUID directMessageId = UUID.randomUUID();

		given(conversationRepository.findById(conversationId)).willReturn(Optional.of(conversation));
		given(conversationParticipantRepository.findByConversationId(conversationId)).willReturn(List.of(participant));
		given(directMessageRepository.findById(directMessageId)).willReturn(Optional.empty());

		// when & then
		assertThatThrownBy(() -> directMessageService.markAsRead(conversationId, directMessageId, requestUserId))
			.isInstanceOf(DirectMessageException.class);
	}

	@Test
	@DisplayName("실패: DM이 해당 대화방 소속이 아니면 예외가 발생한다")
	void markAsRead_Fail_DmNotInConversation() {
		// given
		UUID requestUserId = UUID.randomUUID();
		User user = mock(User.class);
		given(user.getId()).willReturn(requestUserId);

		ConversationParticipant participant = mock(ConversationParticipant.class);
		given(participant.getUser()).willReturn(user);

		UUID conversationId = UUID.randomUUID();
		Conversation conversation = mock(Conversation.class);
		given(conversation.getId()).willReturn(conversationId);

		UUID wrongConversationId = UUID.randomUUID();
		Conversation wrongConversation = mock(Conversation.class);
		given(wrongConversation.getId()).willReturn(wrongConversationId);

		UUID directMessageId = UUID.randomUUID();
		DirectMessage directMessage = mock(DirectMessage.class);
		given(directMessage.getConversation()).willReturn(wrongConversation);

		given(conversationRepository.findById(conversationId)).willReturn(Optional.of(conversation));
		given(conversationParticipantRepository.findByConversationId(conversationId)).willReturn(List.of(participant));
		given(directMessageRepository.findById(directMessageId)).willReturn(Optional.of(directMessage));

		// when & then
		assertThatThrownBy(() -> directMessageService.markAsRead(conversationId, directMessageId, requestUserId))
			.isInstanceOf(DirectMessageException.class);
	}

	@Test
	@DisplayName("실패: DM 수신자가 아니면 예외가 발생한다")
	void markAsRead_Fail_NotReceiver() {
		// given
		UUID requestUserId = UUID.randomUUID();
		User user = mock(User.class);
		given(user.getId()).willReturn(requestUserId);

		ConversationParticipant participant = mock(ConversationParticipant.class);
		given(participant.getUser()).willReturn(user);

		// 요청자와 다른 수신자
		UUID receiverId = UUID.randomUUID();
		User actualReceiver = mock(User.class);
		given(actualReceiver.getId()).willReturn(receiverId);

		UUID conversationId = UUID.randomUUID();
		Conversation conversation = mock(Conversation.class);
		given(conversation.getId()).willReturn(conversationId);

		UUID directMessageId = UUID.randomUUID();
		DirectMessage directMessage = mock(DirectMessage.class);
		given(directMessage.getId()).willReturn(directMessageId);
		given(directMessage.getConversation()).willReturn(conversation);
		given(directMessage.getReceiver()).willReturn(actualReceiver);

		given(conversationRepository.findById(conversationId)).willReturn(Optional.of(conversation));
		given(conversationParticipantRepository.findByConversationId(conversationId)).willReturn(List.of(participant));
		given(directMessageRepository.findById(directMessageId)).willReturn(Optional.of(directMessage));

		// when & then
		assertThatThrownBy(() -> directMessageService.markAsRead(conversationId, directMessageId, requestUserId))
			.isInstanceOf(DirectMessageException.class);
	}

	/*
	=========================
	   DM 목록 조회
	=========================
	 */
	@Test
	@DisplayName("성공: [Cache Hit] Redis에 개수가 캐싱되어 있으면 무거운 DB COUNT 쿼리를 생략한다.")
	void findAll_Success_CacheHit() {
		// given
		UUID conversationId = UUID.randomUUID();
		Conversation conversation = mock(Conversation.class);
		given(conversation.getId()).willReturn(conversationId);

		UUID requestUserId = UUID.randomUUID();
		User mockUser = mock(User.class);
		given(mockUser.getId()).willReturn(requestUserId);

		ConversationParticipant participant = mock(ConversationParticipant.class);
		given(participant.getUser()).willReturn(mockUser);

		DirectMessagePageRequest request = new DirectMessagePageRequest(
			null,
			null,
			2,
			SortDirection.DESCENDING,
			"createdAt"
		);

		given(conversationRepository.findById(conversationId)).willReturn(Optional.of(conversation));
		given(conversationParticipantRepository.findByConversationId(conversationId)).willReturn(List.of(participant));

		long cachedTotalCount = 15L;

		given(directMessageRedisStore.getRoomMessageTotalCount(conversationId)).willReturn(cachedTotalCount);

		// DM 목록 조회 결과
		DirectMessage msg1 = mock(DirectMessage.class);
		given(directMessageRepository.findDirectMessagesByCursor(conversationId, request)).willReturn(List.of(msg1));

		CursorResponseDirectMessageDto expectedResponse = mock(CursorResponseDirectMessageDto.class);
		given(directMessageMapper.toDtoList(anyList())).willReturn(List.of(mock(DirectMessageDto.class)));
		given(directMessageMapper.toCursorPageResponse(anyList(), isNull(), isNull(), eq(false), eq(cachedTotalCount),
			anyString(), anyString()))
			.willReturn(expectedResponse);

		// when
		CursorResponseDirectMessageDto result = directMessageService.findAll(conversationId, request, requestUserId);

		// then
		assertThat(result).isEqualTo(expectedResponse);

		verify(directMessageRepository, never()).findAllByConversationId(any());
		verify(directMessageRedisStore, never()).initDirectMessages(any(), any());
	}

	@Test
	@DisplayName("성공: [Cache Miss] 조회 결과가 limit보다 많으면 hasNext=true로 설정하고 Redis를 백필한다.")
	void findAll_Success_HasNext_CacheMiss() {
		// given
		UUID conversationId = UUID.randomUUID();
		Conversation conversation = mock(Conversation.class);
		given(conversation.getId()).willReturn(conversationId);

		UUID requestUserId = UUID.randomUUID();
		User mockUser = mock(User.class);
		given(mockUser.getId()).willReturn(requestUserId);

		ConversationParticipant participant = mock(ConversationParticipant.class);
		given(participant.getUser()).willReturn(mockUser);

		DirectMessagePageRequest request = new DirectMessagePageRequest(
			null,
			null,
			2,
			SortDirection.DESCENDING,
			"createdAt"
		);

		given(conversationRepository.findById(conversationId)).willReturn(Optional.of(conversation));
		given(conversationParticipantRepository.findByConversationId(conversationId)).willReturn(List.of(participant));

		given(directMessageRedisStore.getRoomMessageTotalCount(conversationId)).willReturn(null);

		// DM 목록 조회 결과: 3개
		DirectMessage msg1 = mock(DirectMessage.class);
		DirectMessage msg2 = mock(DirectMessage.class);
		DirectMessage msg3 = mock(DirectMessage.class);

		String mockCursorTime = Instant.now().toString();
		given(msg2.getCreatedAt()).willReturn(Instant.parse(mockCursorTime));
		UUID msg2Id = UUID.randomUUID();
		given(msg2.getId()).willReturn(msg2Id);

		given(directMessageRepository.findDirectMessagesByCursor(conversationId, request)).willReturn(
			List.of(msg1, msg2, msg3));
		given(directMessageRepository.findAllByConversationId(conversationId)).willReturn(List.of(msg1, msg2, msg3));

		CursorResponseDirectMessageDto expectedResponse = CursorResponseDirectMessageDto.builder()
			.data(Collections.emptyList())
			.nextCursor(null)
			.nextIdAfter(null)
			.hasNext(true)
			.totalCount(0L)
			.sortBy("createdAt")
			.sortDirection("DESCENDING")
			.build();

		// 서브 리스트 검증
		given(directMessageMapper.toDtoList(anyList())).willReturn(
			List.of(mock(DirectMessageDto.class), mock(DirectMessageDto.class)));
		given(directMessageMapper.toCursorPageResponse(anyList(), eq(mockCursorTime), eq(msg2Id), eq(true), eq(3L),
			anyString(), anyString()))
			.willReturn(expectedResponse);

		// when
		CursorResponseDirectMessageDto result = directMessageService.findAll(conversationId, request, requestUserId);

		// then
		assertThat(result).isEqualTo(expectedResponse);

		verify(directMessageRepository, times(1)).findAllByConversationId(conversationId);
		verify(directMessageRedisStore, times(1)).initDirectMessages(eq(conversationId), anyList());
	}

	@Test
	@DisplayName("성공: [Cache Miss] 조회 결과가 빈 리스트일 경우 다음 커서가 null인 빈 응답을 반환하고 빈 리스트를 백필한다.")
	void findAll_Success_EmptyList_CacheMiss() {
		// given
		UUID conversationId = UUID.randomUUID();
		Conversation conversation = mock(Conversation.class);
		given(conversation.getId()).willReturn(conversationId);

		UUID requestUserId = UUID.randomUUID();
		User mockUser = mock(User.class);
		given(mockUser.getId()).willReturn(requestUserId);

		ConversationParticipant participant = mock(ConversationParticipant.class);
		given(participant.getUser()).willReturn(mockUser);

		DirectMessagePageRequest request = new DirectMessagePageRequest(
			null,
			null,
			10,
			SortDirection.DESCENDING,
			"createdAt"
		);

		given(conversationRepository.findById(conversationId)).willReturn(Optional.of(conversation));
		given(conversationParticipantRepository.findByConversationId(conversationId)).willReturn(List.of(participant));

		given(directMessageRedisStore.getRoomMessageTotalCount(conversationId)).willReturn(null);

		given(directMessageRepository.findDirectMessagesByCursor(conversationId, request)).willReturn(List.of());
		given(directMessageRepository.findAllByConversationId(conversationId)).willReturn(List.of());
		given(directMessageMapper.toDtoList(anyList())).willReturn(List.of());

		CursorResponseDirectMessageDto expectedResponse = CursorResponseDirectMessageDto.builder()
			.data(Collections.emptyList())
			.nextCursor(null)
			.nextIdAfter(null)
			.hasNext(false)
			.totalCount(0L)
			.sortBy("createdAt")
			.sortDirection("DESCENDING")
			.build();

		given(directMessageMapper.toCursorPageResponse(anyList(), isNull(), isNull(), eq(false), eq(0L), anyString(),
			anyString()))
			.willReturn(expectedResponse);

		// when
		CursorResponseDirectMessageDto result = directMessageService.findAll(conversationId, request, requestUserId);

		// then
		assertThat(result).isNotNull();

		verify(directMessageRepository, times(1)).findAllByConversationId(conversationId);
		verify(directMessageRedisStore, times(1)).initDirectMessages(conversationId, List.of());
	}

	@Test
	@DisplayName("실패: 대화방이 존재하지 않으면 예외가 발생한다.")
	void findAll_Fail_ConversationNotFound() {
		// given
		UUID conversationId = UUID.randomUUID();
		UUID requestUserId = UUID.randomUUID();
		DirectMessagePageRequest request = new DirectMessagePageRequest(
			null,
			null,
			10,
			SortDirection.DESCENDING,
			"createdAt"
		);

		given(conversationRepository.findById(conversationId)).willReturn(Optional.empty());

		// when & then
		assertThatThrownBy(() -> directMessageService.findAll(conversationId, request, requestUserId))
			.isInstanceOf(ConversationException.class)
			.hasMessageContaining(ConversationErrorCode.CONVERSATION_NOT_FOUND.getMessage());
	}

	@Test
	@DisplayName("실패: 대화방의 참여자가 아니면 권한 예외가 발생한다.")
	void findAll_Fail_NotParticipant() {
		// given
		UUID conversationId = UUID.randomUUID();
		Conversation conversation = mock(Conversation.class);
		given(conversation.getId()).willReturn(conversationId);

		UUID requestUserId = UUID.randomUUID();
		DirectMessagePageRequest request = new DirectMessagePageRequest(
			null,
			null,
			10,
			SortDirection.DESCENDING,
			"createdAt"
		);

		given(conversationRepository.findById(conversationId)).willReturn(Optional.of(conversation));
		given(conversationParticipantRepository.findByConversationId(conversationId)).willReturn(List.of());

		// when & then
		assertThatThrownBy(() -> directMessageService.findAll(conversationId, request, requestUserId))
			.isInstanceOf(ConversationException.class)
			.hasMessageContaining(ConversationErrorCode.CONVERSATION_ACCESS_DENIED.getMessage());
	}

	@Test
	@DisplayName("실패: Repository 조회 중 파라미터(cursor/sortBy) 검증 실패로 인한 예외 발생 시 Service도 예외를 던진다.")
	void findAll_Fail_RepositoryThrowsException() {
		// given
		UUID conversationId = UUID.randomUUID();
		Conversation conversation = mock(Conversation.class);
		given(conversation.getId()).willReturn(conversationId);

		UUID requestUserId = UUID.randomUUID();
		User mockUser = mock(User.class);
		given(mockUser.getId()).willReturn(requestUserId);

		ConversationParticipant participant = mock(ConversationParticipant.class);
		given(participant.getUser()).willReturn(mockUser);

		DirectMessagePageRequest request = mock(DirectMessagePageRequest.class);

		given(conversationRepository.findById(conversationId)).willReturn(Optional.of(conversation));
		given(conversationParticipantRepository.findByConversationId(conversationId))
			.willReturn(List.of(participant));

		// QDSL 예외 발생
		given(directMessageRepository.findDirectMessagesByCursor(conversationId, request))
			.willThrow(new DirectMessageException(DirectMessageErrorCode.DM_INVALID_FORMAT));

		// when & then
		assertThatThrownBy(() -> directMessageService.findAll(conversationId, request, requestUserId))
			.isInstanceOf(DirectMessageException.class);
	}
}