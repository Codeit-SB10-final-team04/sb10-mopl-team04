package com.team04.mopl.conversation.entity;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.team04.mopl.user.entity.User;

class ConversationParticipantTest {

	@Test
	@DisplayName("성공: 올바른 대화와 사용자 정보로 참가자 객체가 생성된다.")
	void createConversationParticipant_Success() {
		// given
		Conversation mockConversation = mock(Conversation.class);
		given(mockConversation.getId()).willReturn(UUID.randomUUID());

		User mockUser = mock(User.class);
		given(mockUser.getId()).willReturn(UUID.randomUUID());

		// when
		ConversationParticipant participant = ConversationParticipant.builder()
			.conversation(mockConversation)
			.user(mockUser)
			.build();

		// then
		assertThat(participant.getConversation()).isEqualTo(mockConversation);
		assertThat(participant.getUser()).isEqualTo(mockUser);
		assertThat(participant.getLastReadAt()).isNull();
		assertThat(participant.getId()).isNotNull();
	}

	@Test
	@DisplayName("성공: 마지막 읽은 시간을 업데이트하면 현재 시간으로 설정된다.")
	void updateLastReadAt_Success() {
		// given
		Conversation mockConversation = mock(Conversation.class);
		User mockUser = mock(User.class);

		ConversationParticipant participant = ConversationParticipant.builder()
			.conversation(mockConversation)
			.user(mockUser)
			.build();

		// when
		participant.updateLastReadAt();

		// then
		assertThat(participant.getLastReadAt()).isNotNull();
	}

	@Test
	@DisplayName("실패: Conversation이 null이면 NullPointerException이 발생한다.")
	void createParticipant_NullConversation_Fail() {
		// given
		User mockUser = mock(User.class);

		// when & then
		assertThatThrownBy(() -> ConversationParticipant.builder()
			.conversation(null)
			.user(mockUser)
			.build())
			.isInstanceOf(NullPointerException.class)
			.hasMessageContaining("참여하고자 하는 대화는 필수입니다");
	}

	@Test
	@DisplayName("실패: User가 null이면 NullPointerException이 발생한다.")
	void createParticipant_NullUser_Fail() {
		// given
		Conversation mockConversation = mock(Conversation.class);

		// when & then
		assertThatThrownBy(() -> ConversationParticipant.builder()
			.conversation(mockConversation)
			.user(null)
			.build())
			.isInstanceOf(NullPointerException.class)
			.hasMessageContaining("대화에 참여하고자 하는 사용자는 필수입니다");
	}
}