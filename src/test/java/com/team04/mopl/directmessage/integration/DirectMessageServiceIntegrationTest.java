package com.team04.mopl.directmessage.integration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

import com.team04.mopl.common.enums.SortDirection;
import com.team04.mopl.conversation.dto.request.ConversationCreateRequest;
import com.team04.mopl.conversation.dto.response.ConversationDto;
import com.team04.mopl.conversation.service.ConversationService;
import com.team04.mopl.directmessage.dto.request.DirectMessagePageRequest;
import com.team04.mopl.directmessage.dto.request.DirectMessageSendRequest;
import com.team04.mopl.directmessage.dto.response.CursorResponseDirectMessageDto;
import com.team04.mopl.directmessage.dto.response.DirectMessageDto;
import com.team04.mopl.directmessage.event.DirectMessageCreatedEvent;
import com.team04.mopl.directmessage.event.DirectMessageReadEvent;
import com.team04.mopl.directmessage.redis.DirectMessageRedisStore;
import com.team04.mopl.directmessage.service.DirectMessageService;
import com.team04.mopl.support.IntegrationTestBase;
import com.team04.mopl.user.entity.User;
import com.team04.mopl.user.repository.UserRepository;

@Transactional
@RecordApplicationEvents
class DirectMessageServiceIntegrationTest extends IntegrationTestBase {

	@Autowired
	private DirectMessageService directMessageService;

	@Autowired
	private ConversationService conversationService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private ApplicationEvents applicationEvents;

	@MockitoBean
	private DirectMessageRedisStore directMessageRedisStore;

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

		ConversationCreateRequest request = new ConversationCreateRequest(receiver.getId());
		ConversationDto conversation = conversationService.createConversation(request, sender.getId());
		conversationId = conversation.id();
	}

	@Test
	@DisplayName("DM 생성 성공: 정상적인 요청 시 메시지가 저장되고 이벤트가 발행된다.")
	void createMessage_Success() {
		// given
		DirectMessageSendRequest request = new DirectMessageSendRequest("안녕하세요!");

		// when
		DirectMessageDto response = directMessageService.create(conversationId, request, sender.getId());

		// then
		assertThat(response).isNotNull();
		assertThat(response.content()).isEqualTo("안녕하세요!");

		long dmEventCount = applicationEvents.stream(DirectMessageCreatedEvent.class).count();
		assertThat(dmEventCount).isEqualTo(1);
	}

	@Test
	@DisplayName("DM 읽음 처리 성공: 수신자가 메시지를 읽음 상태로 변경한다.")
	void markAsRead_Success() {
		// given
		DirectMessageSendRequest request = new DirectMessageSendRequest("안녕하세요!");
		DirectMessageDto dm = directMessageService.create(conversationId, request, sender.getId());

		// when
		directMessageService.markAsRead(conversationId, dm.id(), receiver.getId());

		// then
		long readEventCount = applicationEvents.stream(DirectMessageReadEvent.class).count();
		assertThat(readEventCount).isEqualTo(1);
	}

	@Test
	@DisplayName("DM 목록 조회 성공: 커서 페이징을 통해 메시지 목록을 가져온다.")
	void findAllMessages_Success() {
		// given
		directMessageService.create(conversationId, new DirectMessageSendRequest("Msg 1"), sender.getId());
		directMessageService.create(conversationId, new DirectMessageSendRequest("Msg 2"), sender.getId());

		DirectMessagePageRequest pageRequest = new DirectMessagePageRequest(
			null,
			null,
			10,
			SortDirection.DESCENDING,
			"createdAt"
		);
		when(directMessageRedisStore.getRoomMessageTotalCount(any())).thenReturn(2L);

		// when
		CursorResponseDirectMessageDto response = directMessageService.findAll(conversationId, pageRequest,
			sender.getId());

		// then
		assertThat(response).isNotNull();
		assertThat(response.data()).hasSize(2);
		assertThat(response.totalCount()).isEqualTo(2L);
	}
}
