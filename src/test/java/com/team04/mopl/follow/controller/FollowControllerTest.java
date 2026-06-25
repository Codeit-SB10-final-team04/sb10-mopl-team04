package com.team04.mopl.follow.controller;

import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
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