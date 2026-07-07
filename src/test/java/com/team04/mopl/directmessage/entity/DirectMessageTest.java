package com.team04.mopl.directmessage.entity;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.time.Instant;

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
	@DisplayName("성공: 올바른 정보로 DM 객체가 초기화 상태(안 읽음)로 생성된다.")
	void createDirectMessage_Success() {
		// given
		User sender = mock(User.class);
		User receiver = mock(User.class);

		Conversation conversation = mock(Conversation.class);
		String content = "안녕하세요!";

		// when
		DirectMessage dm = DirectMessage.builder()
			.sender(sender)
			.receiver(receiver)
			.conversation(conversation)
			.content(content)
			.build();

		// then
		assertThat(dm.getContent()).isEqualTo(content);
		assertThat(dm.isRead()).isFalse();
		assertThat(dm.getReadAt()).isNull();
	}

	@Test
	@DisplayName("성공: 이미 읽은 메시지를 다시 읽음 처리해도 readAt이 덮어써지지 않는다 (멱등성 보장)")
	void markAsRead_Idempotent_DoesNotOverwriteReadAt() throws InterruptedException {
		// given
		DirectMessage dm = DirectMessage.builder()
			.sender(mock(User.class))
			.receiver(mock(User.class))
			.conversation(mock(Conversation.class))
			.content("테스트 메시지")
			.build();

		// 첫 번째 호출: 정상적으로 시간이 기록됨
		dm.markAsRead();
		Instant firstReadAt = dm.getReadAt();

		// 두 번째 호출까지 대기 시간
		Thread.sleep(10);

		// when
		dm.markAsRead();

		// then
		assertThat(dm.getReadAt()).isEqualTo(firstReadAt);
	}

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

	@Test
	@DisplayName("성공: 메시지를 읽음 처리하면 상태와 시간이 업데이트된다.")
	void markAsRead_Success() {
		// given
		DirectMessage dm = DirectMessage.builder()
			.sender(mock(User.class))
			.receiver(mock(User.class))
			.conversation(mock(Conversation.class))
			.content("테스트 메시지")
			.build();

		// when
		dm.markAsRead();

		// then
		assertThat(dm.isRead()).isTrue();
		assertThat(dm.getReadAt()).isNotNull();
	}
}