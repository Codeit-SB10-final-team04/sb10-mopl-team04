package com.team04.mopl.conversation.controller;

import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team04.mopl.auth.security.MoplUserDetails;
import com.team04.mopl.auth.security.filter.JwtAuthenticationFilter;
import com.team04.mopl.common.dto.UserSummary;
import com.team04.mopl.conversation.dto.request.ConversationCreateRequest;
import com.team04.mopl.conversation.dto.request.ConversationPageRequest;
import com.team04.mopl.conversation.dto.response.ConversationDto;
import com.team04.mopl.conversation.dto.response.CursorResponseConversationDto;
import com.team04.mopl.conversation.exception.ConversationErrorCode;
import com.team04.mopl.conversation.exception.ConversationException;
import com.team04.mopl.conversation.service.ConversationService;
import com.team04.mopl.directmessage.dto.response.DirectMessageDto;
import com.team04.mopl.user.entity.UserRole;
import com.team04.mopl.user.exception.UserErrorCode;
import com.team04.mopl.user.exception.UserException;

@WebMvcTest(
	controllers = ConversationController.class,
	excludeFilters = @ComponentScan.Filter(
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
		mockSecurityContext(mockUserDetails);

		UUID withUserId = UUID.randomUUID();
		ConversationCreateRequest request = new ConversationCreateRequest(withUserId);
		ConversationDto responseDto = mock(ConversationDto.class);

		given(conversationService.createConversation(any(ConversationCreateRequest.class), eq(requesterUser)))
			.willReturn(responseDto);

		// when & then
		mockMvc.perform(post("/api/conversations")
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
		mockSecurityContext(mockUserDetails);

		ConversationCreateRequest invalidRequest = new ConversationCreateRequest(null);

		// when & then
		mockMvc.perform(post("/api/conversations")
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
		mockSecurityContext(mockUserDetails);

		UUID withUserId = UUID.randomUUID();
		ConversationCreateRequest request = new ConversationCreateRequest(withUserId);

		given(conversationService.createConversation(any(ConversationCreateRequest.class), eq(requesterUser)))
			.willThrow(new ConversationException(ConversationErrorCode.CONVERSATION_ALREADY_EXISTS));

		// when & then
		mockMvc.perform(post("/api/conversations")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andDo(print())
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value(ConversationErrorCode.CONVERSATION_ALREADY_EXISTS.getMessage()));
	}

	/*
	=========================
	   대화 단건 조회
	=========================
	 */
	@Test
	@DisplayName("성공: 유효한 대화방 ID를 전달하면 대화방 단건 정보를 200 OK와 함께 반환한다.")
	void findConversationById_Success() throws Exception {
		// given
		UUID requesterUserId = UUID.randomUUID();
		MoplUserDetails mockUserDetails = MoplUserDetails.authenticated(
			requesterUserId,
			"test@test.com",
			UserRole.USER
		);
		mockSecurityContext(mockUserDetails);

		UUID conversationId = UUID.randomUUID();

		UserSummary withUser = new UserSummary(
			UUID.randomUUID(),
			"상대방",
			"https://profile.img"
		);

		DirectMessageDto latestMessage = new DirectMessageDto(
			UUID.randomUUID(),
			conversationId,
			Instant.now(),
			withUser,
			new UserSummary(UUID.randomUUID(), "나", "https://my.img"),
			"안녕"
		);

		ConversationDto response = ConversationDto.builder()
			.id(conversationId)
			.with(withUser)
			.latestMessage(latestMessage)
			.hasUnread(false)
			.build();

		given(conversationService.findConversationById(eq(conversationId), eq(requesterUserId)))
			.willReturn(response);

		// when & then
		mockMvc.perform(get("/api/conversations/{conversationId}", conversationId)
				.contentType(MediaType.APPLICATION_JSON))
			.andDo(print())
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(conversationId.toString()))
			.andExpect(jsonPath("$.with.name").value("상대방"))
			.andExpect(jsonPath("$.latestMessage.content").value("안녕"))
			.andExpect(jsonPath("$.hasUnread").value(false));
	}

	@Test
	@DisplayName("실패: 존재하지 않는 대화방 ID로 조회하면 404 Not Found를 반환한다.")
	void findConversationById_ConversationNotFound_Fail() throws Exception {
		// given
		UUID requesterUserId = UUID.randomUUID();
		MoplUserDetails mockUserDetails = MoplUserDetails.authenticated(
			requesterUserId,
			"test@test.com",
			UserRole.USER
		);
		mockSecurityContext(mockUserDetails);

		UUID invalidConversationId = UUID.randomUUID();

		given(conversationService.findConversationById(eq(invalidConversationId), eq(requesterUserId)))
			.willThrow(new ConversationException(ConversationErrorCode.CONVERSATION_NOT_FOUND));

		// when & then
		mockMvc.perform(get("/api/conversations/{conversationId}", invalidConversationId)
				.contentType(MediaType.APPLICATION_JSON))
			.andDo(print())
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value(ConversationErrorCode.CONVERSATION_NOT_FOUND.getMessage()));
	}

	@Test
	@DisplayName("실패: 대화방 참여자 중 상대방 유저 정보를 찾을 수 없으면 404 Not Found를 반환한다.")
	void findConversationById_UserNotFound_Fail() throws Exception {
		// given
		UUID requesterUserId = UUID.randomUUID();
		MoplUserDetails mockUserDetails = MoplUserDetails.authenticated(
			requesterUserId,
			"test@test.com",
			UserRole.USER
		);
		mockSecurityContext(mockUserDetails);

		UUID conversationId = UUID.randomUUID();

		given(conversationService.findConversationById(eq(conversationId), eq(requesterUserId)))
			.willThrow(new UserException(UserErrorCode.USER_NOT_FOUND));

		// when & then
		mockMvc.perform(get("/api/conversations/{conversationId}", conversationId)
				.contentType(MediaType.APPLICATION_JSON))
			.andDo(print())
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value(UserErrorCode.USER_NOT_FOUND.getMessage()));
	}

	/*
	=========================
	   특정 사용자와의 대화 조회
	=========================
	 */
	@Test
	@DisplayName("성공: 특정 사용자 조회 요청 시 200 OK 반환")
	void findByUserId_Success() throws Exception {
		// given
		UUID requesterUserId = UUID.randomUUID();
		MoplUserDetails mockUserDetails = MoplUserDetails.authenticated(
			requesterUserId,
			"test@test.com",
			UserRole.USER
		);
		mockSecurityContext(mockUserDetails);

		UUID userId = UUID.randomUUID();
		ConversationDto responseDto = mock(ConversationDto.class);

		given(conversationService.findConversationByUserId(eq(userId), eq(requesterUserId)))
			.willReturn(responseDto);

		mockMvc.perform(get("/api/conversations/with")
				.param("userId", userId.toString()))
			.andExpect(status().isOk());
	}

	@Test
	@DisplayName("실패: 유효하지 않은 userId로 요청 시 404 반환")
	void findByUserId_Fail_UserNotFound() throws Exception {
		// given
		UUID requesterUserId = UUID.randomUUID();
		MoplUserDetails mockUserDetails = MoplUserDetails.authenticated(
			requesterUserId,
			"test@test.com",
			UserRole.USER
		);
		mockSecurityContext(mockUserDetails);

		UUID userId = UUID.randomUUID();

		given(conversationService.findConversationByUserId(any(UUID.class), eq(requesterUserId)))
			.willThrow(new UserException(UserErrorCode.USER_NOT_FOUND));

		// when & then
		mockMvc.perform(get("/api/conversations/with")
				.param("userId", userId.toString()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value(UserErrorCode.USER_NOT_FOUND.getMessage()));
	}

	@Test
	@DisplayName("실패: 상대 유저는 존재하지만 대화방이 없으면 404 Not Found를 반환한다")
	void findByUserId_Fail_ConversationNotFound() throws Exception {
		// given
		UUID requesterUserId = UUID.randomUUID();
		MoplUserDetails mockUserDetails = MoplUserDetails.authenticated(
			requesterUserId,
			"test@test.com",
			UserRole.USER
		);
		mockSecurityContext(mockUserDetails);

		given(conversationService.findConversationByUserId(any(UUID.class), eq(requesterUserId)))
			.willThrow(new ConversationException(ConversationErrorCode.CONVERSATION_NOT_FOUND));

		// when & then
		mockMvc.perform(get("/api/conversations/with")
				.param("userId", UUID.randomUUID().toString()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value(ConversationErrorCode.CONVERSATION_NOT_FOUND.getMessage()));
	}

	/*
	=========================
	   대화 목록 조회
	=========================
	 */
	@Test
	@DisplayName("성공: 올바른 페이징 파라미터가 주어지면 대화 목록을 200 OK와 함께 반환한다.")
	void findAll_Success() throws Exception {
		// given
		UUID requesterUserId = UUID.randomUUID();
		MoplUserDetails mockUserDetails = MoplUserDetails.authenticated(
			requesterUserId,
			"test@test.com",
			UserRole.USER
		);
		mockSecurityContext(mockUserDetails);

		// 빌더 패턴 적용
		CursorResponseConversationDto mockResponse = CursorResponseConversationDto.builder()
			.data(Collections.emptyList())
			.nextCursor(null)
			.nextIdAfter(null)
			.hasNext(false)
			.totalCount(0L)
			.sortBy("createdAt")
			.sortDirection("DESCENDING")
			.build();

		given(conversationService.findAll(any(ConversationPageRequest.class), eq(requesterUserId)))
			.willReturn(mockResponse);

		// when & then
		mockMvc.perform(get("/api/conversations")
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
		UUID requesterUserId = UUID.randomUUID();
		MoplUserDetails mockUserDetails = MoplUserDetails.authenticated(
			requesterUserId,
			"test@test.com",
			UserRole.USER
		);
		mockSecurityContext(mockUserDetails);

		// when & then
		mockMvc.perform(get("/api/conversations")
				.param("limit", "-5")
				.contentType(MediaType.APPLICATION_JSON))
			.andDo(print())
			.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("실패: 잘못된 cursor 형식이 전달되면 ConversationException이 발생하여 400 Bad Request를 반환한다.")
	void findAll_Fail_InvalidCursor() throws Exception {
		// given
		UUID requesterUserId = UUID.randomUUID();
		MoplUserDetails mockUserDetails = MoplUserDetails.authenticated(
			requesterUserId,
			"test@test.com",
			UserRole.USER
		);
		mockSecurityContext(mockUserDetails);

		given(conversationService.findAll(any(ConversationPageRequest.class), eq(requesterUserId)))
			.willThrow(new ConversationException(ConversationErrorCode.CONVERSATION_INVALID_FORMAT));

		// when & then
		mockMvc.perform(get("/api/conversations")
				.param("cursor", "invalid-cursor-string")
				.contentType(MediaType.APPLICATION_JSON))
			.andDo(print())
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value(ConversationErrorCode.CONVERSATION_INVALID_FORMAT.getMessage()));
	}

	@Test
	@DisplayName("실패: 지원하지 않는 sortBy 정렬 기준이 전달되면 ConversationException이 발생하여 400 Bad Request를 반환한다.")
	void findAll_Fail_InvalidSortBy() throws Exception {
		// given
		UUID requesterUserId = UUID.randomUUID();
		MoplUserDetails mockUserDetails = MoplUserDetails.authenticated(
			requesterUserId,
			"test@test.com",
			UserRole.USER
		);
		mockSecurityContext(mockUserDetails);

		given(conversationService.findAll(any(ConversationPageRequest.class), eq(requesterUserId)))
			.willThrow(new ConversationException(ConversationErrorCode.CONVERSATION_INVALID_FORMAT));

		// when & then
		mockMvc.perform(get("/api/conversations")
				.param("sortBy", "unsupportedField")
				.contentType(MediaType.APPLICATION_JSON))
			.andDo(print())
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.message").value(ConversationErrorCode.CONVERSATION_INVALID_FORMAT.getMessage()));
	}
}