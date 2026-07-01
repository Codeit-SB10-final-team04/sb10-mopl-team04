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
}