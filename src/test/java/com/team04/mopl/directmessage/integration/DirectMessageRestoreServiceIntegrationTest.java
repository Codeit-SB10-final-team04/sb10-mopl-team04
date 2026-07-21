package com.team04.mopl.directmessage.integration;

import static org.assertj.core.api.Assertions.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

import jakarta.persistence.EntityManager;

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

	@Autowired
	private EntityManager em;

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

	@Test
	@DisplayName("미읽음 쪽지 복구 조회 실패: 생성된 지 RECOVERY_MINUTES(10분)가 초과된 미읽음 쪽지는 복구 대상에서 제외된다.")
	void findUnreadMessagesAfter_Fail_TimeLimitExceeded() {
		// given
		DirectMessageDto dm = directMessageService.create(
			conversationId,
			new DirectMessageSendRequest("Old Message"),
			sender.getId()
		);

		em.flush();
		em.createNativeQuery("UPDATE direct_messages SET created_at = :time WHERE id = :id")
			.setParameter("time", Instant.now().minus(11, ChronoUnit.MINUTES))
			.setParameter("id", dm.id())
			.executeUpdate();
		em.clear();

		// when
		List<DirectMessageDto> restored = directMessageRestoreService.findUnreadMessagesAfter(receiver.getId(),
			UUID.randomUUID());

		// then
		assertThat(restored).isEmpty();
	}

	@Test
	@DisplayName("미읽음 쪽지 복구 조회 성공: 본인의 것이 아닌 타인의 lastEventId 전달 시 null로 처리되어 Fallback(최근 미읽음 조회)으로 작동한다.")
	void findUnreadMessagesAfter_InvalidEventId_FallbackSuccess() {
		// given
		User anotherUser = userRepository.save(User.builder().
			name("Another")
			.email("another@example.com")
			.build()
		);
		ConversationDto anotherConv = conversationService.createConversation(
			new ConversationCreateRequest(anotherUser.getId()),
			sender.getId()
		);

		// 다른 수신자의 메시지
		DirectMessageDto anotherMessage = directMessageService.create(
			anotherConv.id(),
			new DirectMessageSendRequest("Another message"),
			sender.getId()
		);

		// 내 메시지
		DirectMessageDto myMessage = directMessageService.create(
			conversationId,
			new DirectMessageSendRequest("My message"),
			sender.getId()
		);

		// when
		List<DirectMessageDto> restored = directMessageRestoreService.findUnreadMessagesAfter(receiver.getId(),
			anotherMessage.id());

		// then
		assertThat(restored).hasSize(1);
		assertThat(restored.get(0).id()).isEqualTo(myMessage.id());
	}

	@Test
	@DisplayName("미읽음 쪽지 복구 조회 성공: 이미 읽음 처리(markAsRead)된 메시지는 복구 결과에 포함되지 않는다.")
	void findUnreadMessagesAfter_ExcludeReadMessages_Success() {
		// given
		DirectMessageDto dm1 = directMessageService.create(
			conversationId,
			new DirectMessageSendRequest("Read Message"),
			sender.getId()
		);
		DirectMessageDto dm2 = directMessageService.create(
			conversationId,
			new DirectMessageSendRequest("Unread Message"),
			sender.getId()
		);

		// 첫 번째 메시지를 읽음 처리
		directMessageService.markAsRead(conversationId, dm1.id(), receiver.getId());

		// when
		List<DirectMessageDto> restored = directMessageRestoreService.findUnreadMessagesAfter(receiver.getId(),
			UUID.randomUUID());

		// then
		assertThat(restored).hasSize(1);
		assertThat(restored.get(0).id()).isEqualTo(dm2.id());
	}
}