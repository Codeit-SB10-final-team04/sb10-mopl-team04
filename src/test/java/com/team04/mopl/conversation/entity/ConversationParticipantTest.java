package com.team04.mopl.conversation.entity;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.time.Instant;
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

	@Test
	@DisplayName("성공: 마지막 읽은 시간을 업데이트하면 null에서 현재 시간으로 상태가 전이된다.")
	void updateLastReadAt_StateTransition_Success() {
		// given
		Conversation mockConversation = mock(Conversation.class);
		given(mockConversation.getId()).willReturn(UUID.randomUUID());

		User mockUser = mock(User.class);
		given(mockUser.getId()).willReturn(UUID.randomUUID());

		ConversationParticipant participant = ConversationParticipant.builder()
			.conversation(mockConversation)
			.user(mockUser)
			.build();

		// 검증: 업데이트 전 상태가 null임을 확인 (상태 전이의 시작점 명시)
		assertThat(participant.getLastReadAt()).isNull();

		// when
		participant.updateLastReadAt();

		// then
		// 검증: null에서 값이 존재하는 상태로 전이되었는지 확인
		assertThat(participant.getLastReadAt()).isNotNull();
		assertThat(participant.getLastReadAt()).isBeforeOrEqualTo(Instant.now());
	}
}