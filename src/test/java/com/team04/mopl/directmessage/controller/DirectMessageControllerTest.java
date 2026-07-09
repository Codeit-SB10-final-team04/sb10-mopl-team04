package com.team04.mopl.directmessage.controller;

import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.Collections;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.team04.mopl.auth.security.MoplUserDetails;
import com.team04.mopl.auth.security.filter.JwtAuthenticationFilter;
import com.team04.mopl.conversation.exception.ConversationErrorCode;
import com.team04.mopl.conversation.exception.ConversationException;
import com.team04.mopl.directmessage.dto.request.DirectMessagePagedRequest;
import com.team04.mopl.directmessage.dto.response.CursorResponseDirectMessageDto;
import com.team04.mopl.directmessage.exception.DirectMessageErrorCode;
import com.team04.mopl.directmessage.exception.DirectMessageException;
import com.team04.mopl.directmessage.service.DirectMessageService;
import com.team04.mopl.user.entity.UserRole;

@WebMvcTest(
	controllers = DirectMessageController.class,
	excludeFilters = @ComponentScan.Filter(
		type = FilterType.ASSIGNABLE_TYPE,
		classes = JwtAuthenticationFilter.class
	)
)
@AutoConfigureMockMvc(addFilters = false)
class DirectMessageControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private DirectMessageService directMessageService;

	// 매 테스트마다 context 삭제
	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	// Security 대신 사용자 정보를 수동으로 context에 담는 헬퍼 메서드
	private void mockSecurityContext(MoplUserDetails userDetails) {
		UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
			userDetails,
			null,
			userDetails.getAuthorities()
		);
		SecurityContextHolder.getContext().setAuthentication(auth);
	}

	/*
	=========================
	   DM 읽음 상태 생성
	=========================
	 */
	@Test
	@DisplayName("성공: 올바른 요청이 주어지면 200 OK를 반환한다.")
	void createDirectMessageReadStatus_Success() throws Exception {
		// given
		UUID requesterUserId = UUID.randomUUID();
		MoplUserDetails mockUserDetails = MoplUserDetails.authenticated(
			requesterUserId,
			"test@test.com",
			UserRole.USER
		);
		mockSecurityContext(mockUserDetails);

		UUID conversationId = UUID.randomUUID();
		UUID directMessageId = UUID.randomUUID();

		willDoNothing().given(directMessageService)
			.markAsRead(eq(conversationId), eq(directMessageId), eq(requesterUserId));

		// when & then
		mockMvc.perform(
				post("/api/conversations/{conversationId}/direct-messages/{directMessageId}/read", conversationId,
					directMessageId)
					.contentType(MediaType.APPLICATION_JSON))
			.andDo(print())
			.andExpect(status().isOk());
	}

	@Test
	@DisplayName("실패: 존재하지 않는 대화방 ID로 요청하면 404 Not Found를 반환한다.")
	void createDirectMessageReadStatus_Fail_ConversationNotFound() throws Exception {
		// given
		UUID requesterUserId = UUID.randomUUID();
		MoplUserDetails mockUserDetails = MoplUserDetails.authenticated(
			requesterUserId,
			"test@test.com",
			UserRole.USER
		);
		mockSecurityContext(mockUserDetails);

		UUID conversationId = UUID.randomUUID();
		UUID directMessageId = UUID.randomUUID();

		willThrow(new ConversationException(ConversationErrorCode.CONVERSATION_NOT_FOUND))
			.given(directMessageService).markAsRead(any(), any(), any());

		// when & then
		mockMvc.perform(
				post("/api/conversations/{conversationId}/direct-messages/{directMessageId}/read", conversationId,
					directMessageId)
					.contentType(MediaType.APPLICATION_JSON))
			.andDo(print())
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value(ConversationErrorCode.CONVERSATION_NOT_FOUND.getMessage()));
	}

	@Test
	@DisplayName("실패: 존재하지 않는 DM ID로 요청하면 404 Not Found를 반환한다.")
	void createDirectMessageReadStatus_Fail_DmNotFound() throws Exception {
		// given
		UUID requesterUserId = UUID.randomUUID();
		MoplUserDetails mockUserDetails = MoplUserDetails.authenticated(
			requesterUserId,
			"test@test.com",
			UserRole.USER
		);
		mockSecurityContext(mockUserDetails);

		UUID conversationId = UUID.randomUUID();
		UUID directMessageId = UUID.randomUUID();

		willThrow(new DirectMessageException(DirectMessageErrorCode.DM_NOT_FOUND))
			.given(directMessageService).markAsRead(any(), any(), any());

		// when & then
		mockMvc.perform(
				post("/api/conversations/{conversationId}/direct-messages/{directMessageId}/read", conversationId,
					directMessageId)
					.contentType(MediaType.APPLICATION_JSON))
			.andDo(print())
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value(DirectMessageErrorCode.DM_NOT_FOUND.getMessage()));
	}

	@Test
	@DisplayName("실패: 해당 대화방에 속하지 않은 DM ID를 요청하면 400 Bad Request를 반환한다.")
	void createDirectMessageReadStatus_Fail_DmNotInConversation() throws Exception {
		// given
		UUID requesterUserId = UUID.randomUUID();
		MoplUserDetails mockUserDetails = MoplUserDetails.authenticated(
			requesterUserId,
			"test@test.com",
			UserRole.USER
		);
		mockSecurityContext(mockUserDetails);

		UUID conversationId = UUID.randomUUID();
		UUID directMessageId = UUID.randomUUID();

		willThrow(new DirectMessageException(DirectMessageErrorCode.DM_NOT_IN_CONVERSATION))
			.given(directMessageService).markAsRead(any(), any(), any());

		// when & then
		mockMvc.perform(
				post("/api/conversations/{conversationId}/direct-messages/{directMessageId}/read", conversationId,
					directMessageId)
					.contentType(MediaType.APPLICATION_JSON))
			.andDo(print())
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value(DirectMessageErrorCode.DM_NOT_IN_CONVERSATION.getMessage()));
	}

	@Test
	@DisplayName("실패: 요청자가 DM의 수신자가 아니거나 대화방 참여자가 아니면 403 Forbidden을 반환한다.")
	void createDirectMessageReadStatus_Fail_AccessDenied() throws Exception {
		// given
		UUID requesterUserId = UUID.randomUUID();
		MoplUserDetails mockUserDetails = MoplUserDetails.authenticated(
			requesterUserId,
			"test@test.com",
			UserRole.USER
		);
		mockSecurityContext(mockUserDetails);

		UUID conversationId = UUID.randomUUID();
		UUID directMessageId = UUID.randomUUID();

		willThrow(new DirectMessageException(DirectMessageErrorCode.DM_ACCESS_DENIED))
			.given(directMessageService).markAsRead(any(), any(), any());

		// when & then
		mockMvc.perform(
				post("/api/conversations/{conversationId}/direct-messages/{directMessageId}/read", conversationId,
					directMessageId)
					.contentType(MediaType.APPLICATION_JSON))
			.andDo(print())
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.message").value(DirectMessageErrorCode.DM_ACCESS_DENIED.getMessage()));
	}

	/*
	=========================
	   DM 목록 조회
	=========================
	 */
	@Test
	@DisplayName("성공: 올바른 파라미터로 DM 목록을 조회하면 200 OK를 반환한다.")
	void getDirectMessages_Success() throws Exception {
		// given
		UUID conversationId = UUID.randomUUID();
		UUID requestUserId = UUID.randomUUID();
		MoplUserDetails mockUserDetails = MoplUserDetails.authenticated(
			requestUserId,
			"test@test.com",
			UserRole.USER
		);
		mockSecurityContext(mockUserDetails);

		CursorResponseDirectMessageDto expectedResponse = CursorResponseDirectMessageDto.builder()
			.data(Collections.emptyList())
			.nextCursor(null)
			.nextIdAfter(null)
			.hasNext(false)
			.totalCount(0L)
			.sortBy("createdAt")
			.sortDirection("DESCENDING")
			.build();

		given(directMessageService.findAll(eq(conversationId), any(DirectMessagePagedRequest.class), eq(requestUserId)))
			.willReturn(expectedResponse);

		// when & then
		mockMvc.perform(get("/api/conversations/{conversationId}/direct-messages", conversationId)
				.param("limit", "10")
				.param("sortBy", "createdAt")
				.param("sortDirection", "DESCENDING")
				.contentType(MediaType.APPLICATION_JSON))
			.andDo(print())
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.sortBy").value("createdAt"))
			.andExpect(jsonPath("$.sortDirection").value("DESCENDING"))
			.andExpect(jsonPath("$.hasNext").value(false));
	}

	@Test
	@DisplayName("실패: limit 파라미터에 0 이하의 값이 전달되면 400 Bad Request를 반환한다 (DTO 컴팩트 생성자 검증).")
	void findAll_Fail_InvalidLimit() throws Exception {
		// given
		UUID conversationId = UUID.randomUUID();
		UUID requestUserId = UUID.randomUUID();
		MoplUserDetails mockUserDetails = MoplUserDetails.authenticated(
			requestUserId,
			"test@test.com",
			UserRole.USER
		);
		mockSecurityContext(mockUserDetails);

		// when & then
		mockMvc.perform(get("/api/conversations/{conversationId}/direct-messages", conversationId)
				.param("limit", "-5")
				.contentType(MediaType.APPLICATION_JSON))
			.andDo(print())
			.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("실패: cursor만 전달되고 idAfter가 없으면 400 Bad Request를 반환한다. (DTO 검증)")
	void getDirectMessages_Fail_InvalidCursorFormat() throws Exception {
		// given
		UUID conversationId = UUID.randomUUID();
		UUID requestUserId = UUID.randomUUID();
		MoplUserDetails mockUserDetails = MoplUserDetails.authenticated(
			requestUserId,
			"test@test.com",
			UserRole.USER
		);
		mockSecurityContext(mockUserDetails);

		// when & then
		mockMvc.perform(get("/api/conversations/{conversationId}/direct-messages", conversationId)
				.param("cursor", "invalid-cursor-string")
				.contentType(MediaType.APPLICATION_JSON))
			.andDo(print())
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value("요청 파라미터 유효성 검사에 실패했습니다."))
			.andExpect(jsonPath("$.details._global").value("잘못된 형태의 값이 입력되었습니다."));
	}

	@Test
	@DisplayName("실패: 지원하지 않는 sortBy 정렬 기준이 전달되면 DirectMessageException이 발생하여 400 Bad Request를 반환한다.")
	void findAll_Fail_InvalidSortBy() throws Exception {
		// given
		UUID conversationId = UUID.randomUUID();
		UUID requestUserId = UUID.randomUUID();
		MoplUserDetails mockUserDetails = MoplUserDetails.authenticated(
			requestUserId,
			"test@test.com",
			UserRole.USER
		);
		mockSecurityContext(mockUserDetails);

		given(directMessageService.findAll(eq(conversationId), any(DirectMessagePagedRequest.class), eq(requestUserId)))
			.willThrow(new DirectMessageException(DirectMessageErrorCode.DM_INVALID_FORMAT));

		// when & then
		mockMvc.perform(get("/api/conversations/{conversationId}/direct-messages", conversationId)
				.param("sortBy", "unsupportedField")
				.contentType(MediaType.APPLICATION_JSON))
			.andDo(print())
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value(ConversationErrorCode.CONVERSATION_INVALID_FORMAT.getMessage()));
	}
}