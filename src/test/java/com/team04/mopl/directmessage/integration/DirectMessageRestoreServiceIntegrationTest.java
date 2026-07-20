package com.team04.mopl.directmessage.integration;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

import com.team04.mopl.conversation.dto.request.ConversationCreateRequest;
import com.team04.mopl.conversation.dto.response.ConversationDto;
import com.team04.mopl.conversation.service.ConversationService;
import com.team04.mopl.directmessage.dto.request.DirectMessageSendRequest;
import com.team04.mopl.directmessage.dto.response.DirectMessageDto;
import com.team04.mopl.directmessage.service.DirectMessageRestoreService;
import com.team04.mopl.directmessage.service.DirectMessageService;
import com.team04.mopl.support.IntegrationTestBase;
import com.team04.mopl.user.entity.User;
import com.team04.mopl.user.repository.UserRepository;

@Transactional
@RecordApplicationEvents
class DirectMessageRestoreServiceIntegrationTest extends IntegrationTestBase {

	@Autowired
	private DirectMessageRestoreService directMessageRestoreService;

	@Autowired
	private DirectMessageService directMessageService;

	@Autowired
	private ConversationService conversationService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private ApplicationEvents applicationEvents;

	private User sender;
	private User receiver;
	private UUID conversationId;

	@BeforeEach
	void setUp() {
		sender = userRepository.save(User.builder()
			.name("발신자")
			.email("sender@example.com")
			.build());

		receiver = userRepository.save(User.builder()
			.name("수신자")
			.email("receiver@example.com")
			.build());

		ConversationDto conversation = conversationService.createConversation(
			new ConversationCreateRequest(receiver.getId()),
			sender.getId()
		);
		conversationId = conversation.id();
	}

	@Test
	@DisplayName("미읽음 쪽지 복구 조회 성공: 특정 이벤트 ID 이후의 안 읽은 메시지를 가져온다.")
	void findUnreadMessagesAfter_Success() {
		// given
		DirectMessageDto dm1 = directMessageService.create(
			conversationId,
			new DirectMessageSendRequest("First"),
			sender.getId()
		);
		DirectMessageDto dm2 = directMessageService.create(
			conversationId,
			new DirectMessageSendRequest("Second"),
			sender.getId()
		);

		// when
		List<DirectMessageDto> restored = directMessageRestoreService.findUnreadMessagesAfter(receiver.getId(),
			dm1.id());

		// then
		assertThat(restored).hasSize(1);
		assertThat(restored.get(0).id()).isEqualTo(dm2.id());
	}

	@Test
	@DisplayName("미읽음 쪽지 복구 조회 성공: lastEventId가 없을 경우 최근 미읽음 메시지를 가져온다.")
	void findUnreadMessagesAfter_NullEventId_Success() {
		// given
		directMessageService.create(
			conversationId,
			new DirectMessageSendRequest("Message"),
			sender.getId()
		);

		// when
		List<DirectMessageDto> restored = directMessageRestoreService.findUnreadMessagesAfter(receiver.getId(),
			UUID.randomUUID());

		// then
		assertThat(restored).isNotEmpty();
	}
}
