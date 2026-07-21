package com.team04.mopl.conversation.integration;

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
import com.team04.mopl.conversation.dto.request.ConversationPageRequest;
import com.team04.mopl.conversation.dto.response.ConversationDto;
import com.team04.mopl.conversation.dto.response.CursorResponseConversationDto;
import com.team04.mopl.conversation.event.ConversationCreatedEvent;
import com.team04.mopl.conversation.exception.ConversationErrorCode;
import com.team04.mopl.conversation.exception.ConversationException;
import com.team04.mopl.conversation.redis.ConversationRedisStore;
import com.team04.mopl.conversation.service.ConversationService;
import com.team04.mopl.support.IntegrationTestBase;
import com.team04.mopl.user.entity.User;
import com.team04.mopl.user.exception.UserErrorCode;
import com.team04.mopl.user.exception.UserException;
import com.team04.mopl.user.repository.UserRepository;

@Transactional
@RecordApplicationEvents
class ConversationServiceIntegrationTest extends IntegrationTestBase {

	@Autowired
	private ConversationService conversationService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private ApplicationEvents applicationEvents;

	@MockitoBean
	private ConversationRedisStore conversationRedisStore;

	private User requestUser;
	private User withUser;

	@BeforeEach
	void setUp() {
		requestUser = userRepository.save(User.builder()
			.name("요청자")
			.email("request@example.com")
			.build());

		withUser = userRepository.save(User.builder()
			.name("상대방")
			.email("with@example.com")
			.build());
	}

	@Test
	@DisplayName("대화 생성 성공: 유효한 요청 시 대화방 및 참여자가 생성되고 이벤트가 발행된다.")
	void createConversation_Success() {
		// given
		ConversationCreateRequest request = new ConversationCreateRequest(withUser.getId());

		// when
		ConversationDto response = conversationService.createConversation(request, requestUser.getId());

		// then
		assertThat(response).isNotNull();
		assertThat(response.with().userId()).isEqualTo(withUser.getId());

		long eventCount = applicationEvents.stream(ConversationCreatedEvent.class).count();
		assertThat(eventCount).isEqualTo(1);
	}

	@Test
	@DisplayName("대화 생성 실패: 이미 대화방이 존재하는 경우 예외가 발생한다.")
	void createConversation_Fail_Duplicate() {
		// given
		ConversationCreateRequest request = new ConversationCreateRequest(withUser.getId());
		conversationService.createConversation(request, requestUser.getId()); // 첫 번째 생성

		// when & then
		assertThatThrownBy(() -> conversationService.createConversation(request, requestUser.getId()))
			.isInstanceOf(ConversationException.class)
			.hasMessageContaining(ConversationErrorCode.CONVERSATION_ALREADY_EXISTS.getMessage());
	}

	@Test
	@DisplayName("대화 단건 조회 성공: 정상적인 권한으로 특정 사용자와의 대화방을 조회한다.")
	void findConversationByUserId_Success() {
		// given
		ConversationCreateRequest request = new ConversationCreateRequest(withUser.getId());
		conversationService.createConversation(request, requestUser.getId());
		when(conversationRedisStore.getConversationId(any(), any())).thenReturn(null);

		// when
		ConversationDto response = conversationService.findConversationByUserId(withUser.getId(), requestUser.getId());

		// then
		assertThat(response).isNotNull();
		assertThat(response.with().userId()).isEqualTo(withUser.getId());
	}

	@Test
	@DisplayName("대화 단건 조회 실패: 생성되지 않은 공통 대화방을 조회할 경우 예외가 발생한다.")
	void findConversationByUserId_Fail_NotFound() {
		// given
		// requestUser와 withUser 사이에 대화방을 생성하지 않은 상태
		when(conversationRedisStore.getConversationId(any(), any())).thenReturn(null);

		// when & then
		assertThatThrownBy(() -> conversationService.findConversationByUserId(withUser.getId(), requestUser.getId()))
			.isInstanceOf(ConversationException.class)
			.hasMessageContaining(ConversationErrorCode.CONVERSATION_NOT_FOUND.getMessage());
	}

	@Test
	@DisplayName("대화 단건 조회 실패: 존재하지 않는 상대방과의 대화를 조회할 경우 예외가 발생한다.")
	void findConversationByUserId_Fail_UserNotFound() {
		// given
		UUID nonExistentUserId = UUID.randomUUID();

		// when & then
		assertThatThrownBy(() -> conversationService.findConversationByUserId(nonExistentUserId, requestUser.getId()))
			.isInstanceOf(UserException.class)
			.hasMessageContaining(UserErrorCode.USER_NOT_FOUND.getMessage());
	}

	@Test
	@DisplayName("대화 목록 조회 성공: 빈 페이지 요청 시 올바른 커서 응답을 반환한다.")
	void findAll_Success_Empty() {
		// given
		ConversationPageRequest request = new ConversationPageRequest(
			null,
			null,
			null,
			10,
			SortDirection.DESCENDING,
			"createdAt"
		);

		// when
		CursorResponseConversationDto response = conversationService.findAll(request, requestUser.getId());

		// then
		assertThat(response).isNotNull();
		assertThat(response.data()).isEmpty();
		assertThat(response.hasNext()).isFalse();
	}
}