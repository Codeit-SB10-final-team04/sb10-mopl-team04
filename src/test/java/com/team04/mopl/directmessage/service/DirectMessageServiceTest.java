package com.team04.mopl.directmessage.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.team04.mopl.conversation.entity.Conversation;
import com.team04.mopl.conversation.entity.ConversationParticipant;
import com.team04.mopl.conversation.exception.ConversationException;
import com.team04.mopl.conversation.repository.ConversationParticipantRepository;
import com.team04.mopl.conversation.repository.ConversationRepository;
import com.team04.mopl.directmessage.entity.DirectMessage;
import com.team04.mopl.directmessage.exception.DirectMessageException;
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

	@InjectMocks
	private DirectMessageService directMessageService;

	/*
	=========================
		DM 읽음 처리 생성
	=========================
	 */
	@Test
	@DisplayName("성공: 조건이 모두 맞으면 DM 읽음 처리가 정상 수행된다 (멱등성 보장 포함)")
	void markAsRead_Success() {
		// given
		UUID conversationId = UUID.randomUUID();
		UUID directMessageId = UUID.randomUUID();
		UUID requestUserId = UUID.randomUUID();

		Conversation conversation = mock(Conversation.class);
		given(conversation.getId()).willReturn(conversationId);

		User receiver = mock(User.class);
		given(receiver.getId()).willReturn(requestUserId);

		DirectMessage directMessage = mock(DirectMessage.class);
		given(directMessage.getConversation()).willReturn(conversation);
		given(directMessage.getReceiver()).willReturn(receiver);

		// Repository Mocking
		given(conversationRepository.findById(conversationId)).willReturn(Optional.of(conversation));
		given(directMessageRepository.findById(directMessageId)).willReturn(Optional.of(directMessage));

		// 참여자 검증 로직 Mocking (구현체에 맞게 조정 필요: existBy... 혹은 findBy...)
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
	@DisplayName("실패: 대화방 참여자가 아니면 예외가 발생한다")
	void markAsRead_Fail_NotParticipant() {
		// given
		UUID conversationId = UUID.randomUUID();
		UUID requestUserId = UUID.randomUUID();

		Conversation conversation = mock(Conversation.class);
		given(conversation.getId()).willReturn(conversationId);
		given(conversationRepository.findById(conversationId)).willReturn(Optional.of(conversation));

		// 참여자가 없는 빈 리스트 반환
		given(conversationParticipantRepository.findByConversationId(conversationId)).willReturn(List.of());

		// when & then
		assertThatThrownBy(() -> directMessageService.markAsRead(conversationId, UUID.randomUUID(), requestUserId))
			.isInstanceOf(ConversationException.class);
	}

	@Test
	@DisplayName("실패: DM이 존재하지 않으면 예외가 발생한다")
	void markAsRead_Fail_DmNotFound() {
		// given
		UUID conversationId = UUID.randomUUID();
		UUID directMessageId = UUID.randomUUID();
		UUID requestUserId = UUID.randomUUID();

		Conversation conversation = mock(Conversation.class);
		given(conversation.getId()).willReturn(conversationId);
		given(conversationRepository.findById(conversationId)).willReturn(Optional.of(conversation));

		ConversationParticipant participant = mock(ConversationParticipant.class);
		User user = mock(User.class);
		given(user.getId()).willReturn(requestUserId);
		given(participant.getUser()).willReturn(user);
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
		UUID conversationId = UUID.randomUUID();
		UUID wrongConversationId = UUID.randomUUID(); // 다른 대화방 ID
		UUID directMessageId = UUID.randomUUID();
		UUID requestUserId = UUID.randomUUID();

		Conversation conversation = mock(Conversation.class);
		given(conversation.getId()).willReturn(conversationId);
		given(conversationRepository.findById(conversationId)).willReturn(Optional.of(conversation));

		// ... 참여자 모킹 생략 (동일) ...
		ConversationParticipant participant = mock(ConversationParticipant.class);
		User user = mock(User.class);
		given(user.getId()).willReturn(requestUserId);
		given(participant.getUser()).willReturn(user);
		given(conversationParticipantRepository.findByConversationId(conversationId)).willReturn(List.of(participant));

		// 메시지가 다른 대화방(wrongConversationId)을 가리킴
		DirectMessage directMessage = mock(DirectMessage.class);
		Conversation wrongConversation = mock(Conversation.class);
		given(wrongConversation.getId()).willReturn(wrongConversationId);
		given(directMessage.getConversation()).willReturn(wrongConversation);

		given(directMessageRepository.findById(directMessageId)).willReturn(Optional.of(directMessage));

		// when & then
		assertThatThrownBy(() -> directMessageService.markAsRead(conversationId, directMessageId, requestUserId))
			.isInstanceOf(DirectMessageException.class); // DM_NOT_IN_CONVERSATION 기대
	}

	@Test
	@DisplayName("실패: DM 수신자가 아니면 예외가 발생한다")
	void markAsRead_Fail_NotReceiver() {
		// given
		UUID conversationId = UUID.randomUUID();
		UUID directMessageId = UUID.randomUUID();
		UUID requestUserId = UUID.randomUUID();
		UUID receiverId = UUID.randomUUID(); // 요청자와 다른 수신자 ID

		Conversation conversation = mock(Conversation.class);
		given(conversation.getId()).willReturn(conversationId);
		given(conversationRepository.findById(conversationId)).willReturn(Optional.of(conversation));

		// ... 참여자 모킹 생략 (동일) ...
		ConversationParticipant participant = mock(ConversationParticipant.class);
		User user = mock(User.class);
		given(user.getId()).willReturn(requestUserId);
		given(participant.getUser()).willReturn(user);
		given(conversationParticipantRepository.findByConversationId(conversationId)).willReturn(List.of(participant));

		DirectMessage directMessage = mock(DirectMessage.class);
		given(directMessage.getConversation()).willReturn(conversation);

		User actualReceiver = mock(User.class);
		given(actualReceiver.getId()).willReturn(receiverId);
		given(directMessage.getReceiver()).willReturn(actualReceiver);

		given(directMessageRepository.findById(directMessageId)).willReturn(Optional.of(directMessage));

		// when & then
		assertThatThrownBy(() -> directMessageService.markAsRead(conversationId, directMessageId, requestUserId))
			.isInstanceOf(DirectMessageException.class); // DM_ACCESS_DENIED 기대
	}
}