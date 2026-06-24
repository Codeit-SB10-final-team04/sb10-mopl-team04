package com.team04.mopl.user.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.team04.mopl.user.dto.request.UserCreateRequest;
import com.team04.mopl.user.dto.response.UserDto;
import com.team04.mopl.user.entity.UserRole;
import com.team04.mopl.user.service.UserService;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private UserService userService;

	@Test
	@DisplayName("사용자 등록 요청이 유효하면 201과 사용자 정보를 반환한다")
	void create_returnCreatedUser_whenRequestIsValid() throws Exception {
		// given
		UUID userId = UUID.randomUUID();
		Instant createdAt = Instant.parse("2026-06-24T00:00:00Z");

		UserDto response = new UserDto(
			userId,
			createdAt,
			"test@test.com",
			"사용자",
			"https://example.com/profile.png",
			UserRole.USER,
			false
		);

		given(userService.create(new UserCreateRequest(
			"사용자",
			"test@test.com",
			"password123"
		))).willReturn(response);

		// when & then
		mockMvc.perform(post("/api/users")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"사용자\",\"email\":\"test@test.com\",\"password\":\"password123\"}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").value(userId.toString()))
			.andExpect(jsonPath("$.email").value("test@test.com"))
			.andExpect(jsonPath("$.name").value("사용자"))
			.andExpect(jsonPath("$.profileImageUrl").value("https://example.com/profile.png"))
			.andExpect(jsonPath("$.role").value("USER"))
			.andExpect(jsonPath("$.locked").value(false));
	}

	@Test
	@DisplayName("이메일 형식이 올바르지 않으면 400을 반환한다")
	void create_returnBadRequest_whenEmailIsInvalid() throws Exception {
		// given

		// when & then
		mockMvc.perform(post("/api/users")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"사용자\",\"email\":\"invalid-email\",\"password\":\"password123\"}"))
			.andExpect(status().isBadRequest());

		verifyNoInteractions(userService);
	}

	@Test
	@DisplayName("비밀번호가 8자 미만이면 400을 반환한다")
	void create_returnBadRequest_whenPasswordIsShorterThanEightCharacters() throws Exception {
		// given

		// when & then
		mockMvc.perform(post("/api/users")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"사용자\",\"email\":\"test@test.com\",\"password\":\"1234567\"}"))
			.andExpect(status().isBadRequest());

		verifyNoInteractions(userService);
	}

	@Test
	@DisplayName("이름이 공백이면 400을 반환한다")
	void create_returnBadRequest_whenNameIsBlank() throws Exception {
		// given

		// when & then
		mockMvc.perform(post("/api/users")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"   \",\"email\":\"test@test.com\",\"password\":\"password123\"}"))
			.andExpect(status().isBadRequest());

		verifyNoInteractions(userService);
	}

	@Test
	@DisplayName("이메일이 공백이면 400을 반환한다")
	void create_returnBadRequest_whenEmailIsBlank() throws Exception {
		// given

		// when & then
		mockMvc.perform(post("/api/users")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"사용자\",\"email\":\"   \",\"password\":\"password123\"}"))
			.andExpect(status().isBadRequest());

		verifyNoInteractions(userService);
	}

	@Test
	@DisplayName("비밀번호가 공백이면 400을 반환한다")
	void create_returnBadRequest_whenPasswordIsBlank() throws Exception {
		// given

		// when & then
		mockMvc.perform(post("/api/users")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"사용자\",\"email\":\"test@test.com\",\"password\":\"   \"}"))
			.andExpect(status().isBadRequest());

		verifyNoInteractions(userService);
	}
}