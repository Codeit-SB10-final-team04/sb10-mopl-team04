package com.team04.mopl.conversation.controller;

import static org.mockito.BDDMockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team04.mopl.auth.security.MoplUserDetails;
import com.team04.mopl.auth.security.filter.JwtAuthenticationFilter;
import com.team04.mopl.conversation.dto.request.ConversationCreateRequest;
import com.team04.mopl.conversation.dto.response.ConversationDto;
import com.team04.mopl.conversation.exception.ConversationErrorCode;
import com.team04.mopl.conversation.exception.ConversationException;
import com.team04.mopl.conversation.service.ConversationService;
import com.team04.mopl.user.entity.UserRole;

@WebMvcTest(
	controllers = ConversationController.class,
	excludeFilters = @ComponentScan.Filter( // 컨트롤러 테스트에서 JWT 인증 필터 제외
		type = FilterType.ASSIGNABLE_TYPE,
		classes = JwtAuthenticationFilter.class
	)
)
@AutoConfigureMockMvc(addFilters = false)
class ConversationControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private ConversationService conversationService;

	/*
	=========================
		대화 생성
	=========================
	 */
	@Test
	@DisplayName("성공: 올바른 요청이 주어지면 201 Created를 반환한다.")
	void createConversation_Success() throws Exception {
		// given
		UUID requesterUser = UUID.randomUUID();
		MoplUserDetails mockUserDetails = MoplUserDetails.authenticated(
			requesterUser,
			"test@test.com",
			UserRole.USER
		);

		UUID withUserId = UUID.randomUUID();
		ConversationCreateRequest request = new ConversationCreateRequest(withUserId);
		ConversationDto responseDto = mock(ConversationDto.class);

		given(conversationService.createConversation(any(ConversationCreateRequest.class), any(MoplUserDetails.class)))
			.willReturn(responseDto);

		// when & then
		mockMvc.perform(post("/api/conversations")
				// security 인증 객체 주입
				.with(user(mockUserDetails))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andDo(print())
			.andExpect(status().isCreated());
	}

	@Test
	@DisplayName("실패: 요청 바디에 필수값(withUserId)이 누락되면 400 Bad Request를 반환한다.")
	void createConversation_InvalidBody_Fail() throws Exception {
		// given
		UUID requesterUser = UUID.randomUUID();
		MoplUserDetails mockUserDetails = MoplUserDetails.authenticated(
			requesterUser,
			"test@test.com",
			UserRole.USER
		);

		// 요청 DTO 내 대화 상대 ID 값 (withUserId) 누락
		ConversationCreateRequest invalidRequest = new ConversationCreateRequest(null);

		// when & then
		mockMvc.perform(post("/api/conversations")
				.with(user(mockUserDetails))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(invalidRequest)))
			.andDo(print())
			.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("실패: 이미 존재하는 대화방 생성을 시도하면 409 Conflict를 반환한다.")
	void createConversation_Duplicate_Fail() throws Exception {
		// given
		UUID requesterUser = UUID.randomUUID();
		MoplUserDetails mockUserDetails = MoplUserDetails.authenticated(
			requesterUser,
			"test@test.com",
			UserRole.USER
		);

		UUID withUserId = UUID.randomUUID();
		ConversationCreateRequest request = new ConversationCreateRequest(withUserId);

		given(conversationService.createConversation(any(), any()))
			.willThrow(new ConversationException(ConversationErrorCode.CONVERSATION_ALREADY_EXISTS));

		// when & then
		mockMvc.perform(post("/api/conversations")
				.with(user(mockUserDetails))
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andDo(print())
			.andExpect(status().isConflict());
	}
}