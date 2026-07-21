package com.team04.mopl.directmessage.entity;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.team04.mopl.conversation.entity.Conversation;
import com.team04.mopl.directmessage.exception.DirectMessageErrorCode;
import com.team04.mopl.directmessage.exception.DirectMessageException;
import com.team04.mopl.user.entity.User;

class DirectMessageTest {

	@Test
	@DisplayName("실패: 메시지 내용이 null이면 예외가 발생한다.")
	void createDirectMessage_NullContent_Fail() {
		// when & then
		assertThatThrownBy(() -> DirectMessage.builder()
			.sender(mock(User.class))
			.receiver(mock(User.class))
			.conversation(mock(Conversation.class))
			.content(null)
			.build())
			.isInstanceOf(DirectMessageException.class)
			.hasMessageContaining(DirectMessageErrorCode.DM_BLANK.getMessage());
	}

	@ParameterizedTest
	@ValueSource(strings = {"", " ", "   "})
	@DisplayName("실패: 메시지 내용이 비어있거나 공백만 있으면 예외가 발생한다.")
	void createDirectMessage_BlankContent_Fail(String blankContent) {
		// when & then
		assertThatThrownBy(() -> DirectMessage.builder()
			.sender(mock(User.class))
			.receiver(mock(User.class))
			.conversation(mock(Conversation.class))
			.content(blankContent)
			.build())
			.isInstanceOf(DirectMessageException.class)
			.hasMessageContaining(DirectMessageErrorCode.DM_BLANK.getMessage());
	}
}