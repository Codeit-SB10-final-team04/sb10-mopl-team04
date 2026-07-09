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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.team04.mopl.common.enums.SortDirection;
import com.team04.mopl.conversation.entity.Conversation;
import com.team04.mopl.conversation.entity.ConversationParticipant;
import com.team04.mopl.conversation.exception.ConversationErrorCode;
import com.team04.mopl.conversation.exception.ConversationException;
import com.team04.mopl.conversation.repository.ConversationParticipantRepository;
import com.team04.mopl.conversation.repository.ConversationRepository;
import com.team04.mopl.directmessage.dto.request.DirectMessagePagedRequest;
import com.team04.mopl.directmessage.dto.response.CursorResponseDirectMessageDto;
import com.team04.mopl.directmessage.dto.response.DirectMessageDto;
import com.team04.mopl.directmessage.entity.DirectMessage;
import com.team04.mopl.directmessage.exception.DirectMessageErrorCode;
import com.team04.mopl.directmessage.exception.DirectMessageException;
import com.team04.mopl.directmessage.mapper.DirectMessageMapper;
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

	@InjectMocks
	private DirectMessageService directMessageService;

	/*
	=========================
		DM 읽음 처리 생성
	=========================
	 */
	@Test
	@DisplayName("성공: 조건이 모두 맞으면 DM 읽음 처리가 정상 수행된다")
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
		verify(directMessage).markAsRead(); // 읽음 처리 메서드가 호출되었는지 확인
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
		given(directMessage.getConversation()).willReturn(conversation);
		given(directMessage.getReceiver()).willReturn(actualReceiver);

		given(conversationRepository.findById(conversationId)).willReturn(Optional.of(conversation));
		given(conversationParticipantRepository.findByConversationId(conversationId)).willReturn(List.of(participant));
		given(directMessageRepository.findById(directMessageId)).willReturn(Optional.of(directMessage));

		// when & then
		assertThatThrownBy(() -> directMessageService.markAsRead(conversationId, directMessageId, requestUserId))
			.isInstanceOf(DirectMessageException.class); // DM_ACCESS_DENIED 기대
	}

	/*
	=========================
	   DM 목록 조회
	=========================
	 */
	@Test
	@DisplayName("성공: 조회 결과가 limit보다 많으면 hasNext=true로 설정하고 다음 커서를 계산하여 반환한다.")
	void findAll_Success_HasNext() {
		// given
		UUID conversationId = UUID.randomUUID();
		Conversation conversation = mock(Conversation.class);
		given(conversation.getId()).willReturn(conversationId);

		UUID requestUserId = UUID.randomUUID();
		User mockUser = mock(User.class);
		given(mockUser.getId()).willReturn(requestUserId);

		ConversationParticipant participant = mock(ConversationParticipant.class);
		given(participant.getUser()).willReturn(mockUser);

		DirectMessagePagedRequest request = new DirectMessagePagedRequest(
			null,
			null,
			2,
			SortDirection.DESCENDING,
			"createdAt"
		);

		given(conversationRepository.findById(conversationId)).willReturn(Optional.of(conversation));
		given(conversationParticipantRepository.findByConversationId(conversationId)).willReturn(List.of(participant));

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
		given(directMessageRepository.countDirectMessage(conversationId, request)).willReturn(3L);

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
	}

	@Test
	@DisplayName("성공: 조회 결과가 빈 리스트일 경우 다음 커서가 null인 빈 응답을 반환한다.")
	void findAll_Success_EmptyList() {
		// given
		UUID conversationId = UUID.randomUUID();
		Conversation conversation = mock(Conversation.class);
		given(conversation.getId()).willReturn(conversationId);

		UUID requestUserId = UUID.randomUUID();
		User mockUser = mock(User.class);
		given(mockUser.getId()).willReturn(requestUserId);

		ConversationParticipant participant = mock(ConversationParticipant.class);
		given(participant.getUser()).willReturn(mockUser);

		DirectMessagePagedRequest request = new DirectMessagePagedRequest(
			null,
			null,
			10,
			SortDirection.DESCENDING,
			"createdAt"
		);

		given(conversationRepository.findById(conversationId)).willReturn(Optional.of(conversation));
		given(conversationParticipantRepository.findByConversationId(conversationId)).willReturn(List.of(participant));
		given(directMessageRepository.findDirectMessagesByCursor(conversationId, request)).willReturn(List.of());
		given(directMessageRepository.countDirectMessage(conversationId, request)).willReturn(0L);
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
	}

	@Test
	@DisplayName("실패: 대화방이 존재하지 않으면 예외가 발생한다.")
	void findAll_Fail_ConversationNotFound() {
		// given
		UUID conversationId = UUID.randomUUID();
		UUID requestUserId = UUID.randomUUID();
		DirectMessagePagedRequest request = new DirectMessagePagedRequest(
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
		DirectMessagePagedRequest request = new DirectMessagePagedRequest(
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

		DirectMessagePagedRequest request = mock(DirectMessagePagedRequest.class);

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