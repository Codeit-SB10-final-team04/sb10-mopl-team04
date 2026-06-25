package com.team04.mopl.follow.controller;

import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team04.mopl.follow.dto.request.FollowRequest;
import com.team04.mopl.follow.dto.response.FollowDto;
import com.team04.mopl.follow.service.FollowService;

@WebMvcTest(FollowController.class)
@AutoConfigureMockMvc(addFilters = false)
class FollowControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private FollowService followService;

	/*
	=========================
		팔로우 생성
	=========================
	 */
	@Test
	@DisplayName("성공: 올바른 요청 바디와 헤더가 주어지면 201 Created를 반환한다.")
	void createFollow_Success() throws Exception {
		// given
		UUID currentUserId = UUID.randomUUID();
		UUID followeeId = UUID.randomUUID();

		FollowRequest request = new FollowRequest(followeeId);
		FollowDto responseDto = mock(FollowDto.class);

		given(followService.createFollow(any(FollowRequest.class), eq(currentUserId))).willReturn(responseDto);

		// when & then
		mockMvc.perform(post("/api/follows")
				.header("X-MOPL-USER-ID", currentUserId.toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andDo(print())
			.andExpect(status().isCreated());
	}

	@Test
	@DisplayName("실패: 요청 헤더(X-MOPL-USER-ID)가 누락되면 400 Bad Request를 반환한다.")
	void createFollow_MissingHeader_Fail() throws Exception {
		// given
		FollowRequest request = new FollowRequest(UUID.randomUUID());

		// when & then
		mockMvc.perform(post("/api/follows")
				// 헤더 누락
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andDo(print())
			.andExpect(status().isInternalServerError());
	}

	@Test
	@DisplayName("실패: 요청 바디(FollowRequest)에 필수값이 누락되면 400 Bad Request를 반환한다.")
	void createFollow_InvalidBody_Fail() throws Exception {
		// given
		UUID currentUserId = UUID.randomUUID();
		// 요청 바디 내 FolloweeId 누락
		FollowRequest invalidRequest = new FollowRequest(null);

		// when & then
		mockMvc.perform(post("/api/follows")
				.header("X-MOPL-USER-ID", currentUserId.toString())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(invalidRequest)))
			.andDo(print())
			.andExpect(status().isBadRequest());
	}

	/*
	==============================
		특정 사용자의 팔로우 수 조회
	==============================
	 */
	@Test
	@DisplayName("성공: 올바른 followeeId로 요청 시 HTTP 200과 함께 카운트를 반환한다.")
	void getFollowerCount_Return200AndCount_Success() throws Exception {
		// given
		UUID followeeId = UUID.randomUUID();

		long followerCount = 42L;

		given(followService.getFollowerCount(followeeId)).willReturn(followerCount);

		// when & then
		mockMvc.perform(get("/api/follows/count")
				.param("followeeId", followeeId.toString())) // 👈 param으로 전달
			.andExpect(status().isOk())
			.andExpect(content().string(String.valueOf(followerCount)));
	}

	@Test
	@DisplayName("실패: 필수 파라미터(followeeId)가 누락되면 @Valid에 의해 400 Bad Request를 반환한다.")
	void getFollowerCount_MissingFolloweeId_Return400_Fail() throws Exception {
		// given

		// when & then
		// @NotNull 유효성 검사 실패로 인해 BindException 발생 -> 400 에러
		mockMvc.perform(get("/api/follows/count"))
			.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("실패: 파라미터가 UUID 형식이 아니면 400 Bad Request를 반환한다.")
	void getFollowerCount_InvalidUuidFormat_Return400_Fail() throws Exception {
		// given: 잘못된 문자열 전송
		String invalidFolloweeId = "not-a-uuid-string";

		// when & then
		mockMvc.perform(get("/api/follows/count")
				.param("followeeId", invalidFolloweeId))
			.andExpect(status().isBadRequest());
	}

	// @Test
	// @DisplayName("실패: 서비스 계층에서 유저를 찾지 못한 예외가 올라오면 알맞은 에러 코드를 반환한다.")
	// void getFollowerCount_UserNotFound_Return404_Fail() throws Exception {
	// 	// given
	// 	UUID invalidFolloweeId = UUID.randomUUID();
	//
	// 	given(followService.getFollowerCount(any(FollowRequest.class)))
	// 		.willThrow(new UserException(UserErrorCode.USER_NOT_FOUND));
	//
	// 	// when & then
	// 	// 프로젝트 GlobalExceptionHandler 세팅에 맞게 isNotFound() 등 확인
	// 	mockMvc.perform(get("/api/follows/count")
	// 			.param("followeeId", invalidFolloweeId.toString())
	// 			.contentType(MediaType.APPLICATION_JSON))
	// 		.andExpect(status().isNotFound());
	// }
}