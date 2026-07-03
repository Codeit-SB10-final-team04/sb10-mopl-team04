package com.team04.mopl.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.team04.mopl.auth.security.filter.JwtAuthenticationFilter;
import com.team04.mopl.common.exception.GlobalExceptionHandler;
import com.team04.mopl.user.dto.request.UserCreateRequest;
import com.team04.mopl.user.dto.request.UserRoleUpdateRequest;
import com.team04.mopl.user.dto.response.UserDto;
import com.team04.mopl.user.entity.UserRole;
import com.team04.mopl.user.exception.UserErrorCode;
import com.team04.mopl.user.exception.UserException;
import com.team04.mopl.user.service.UserAdminService;
import com.team04.mopl.user.service.UserService;

@WebMvcTest(
	controllers = UserController.class,
	excludeFilters = @ComponentScan.Filter( // 컨트롤러 테스트에서 JWT 인증 필터 제외
		type = FilterType.ASSIGNABLE_TYPE,
		classes = JwtAuthenticationFilter.class
	)
)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class UserControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private UserService userService;

	@MockitoBean
	private UserAdminService userAdminService;

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
				.content("""
					{
						"name": "사용자",
						"email": "test@test.com",
						"password": "password123"
					}
					"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").value(userId.toString()))
			.andExpect(jsonPath("$.email").value("test@test.com"))
			.andExpect(jsonPath("$.name").value("사용자"))
			.andExpect(jsonPath("$.profileImageUrl").value("https://example.com/profile.png"))
			.andExpect(jsonPath("$.role").value("USER"))
			.andExpect(jsonPath("$.locked").value(false));
	}

	@Test
	@DisplayName("이미 사용 중인 이메일이면 400과 에러 응답을 반환한다")
	void create_returnBadRequest_whenEmailAlreadyExists() throws Exception {
		// given
		given(userService.create(any(UserCreateRequest.class)))
			.willThrow(new UserException(
				UserErrorCode.USER_EMAIL_ALREADY_EXISTS,
				Map.of("email", "test@test.com")
			));

		// when & then
		mockMvc.perform(post("/api/users")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
						"name": "사용자",
						"email": "test@test.com",
						"password": "password123"
					}
					"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.exceptionName").value("UserException"))
			.andExpect(jsonPath("$.message").value("이미 사용 중인 이메일입니다."))
			.andExpect(jsonPath("$.details.email").value("test@test.com"));
	}

	@Test
	@DisplayName("이메일 형식이 올바르지 않으면 400을 반환한다")
	void create_returnBadRequest_whenEmailIsInvalid() throws Exception {
		// given

		// when & then
		mockMvc.perform(post("/api/users")
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
						"name": "사용자",
						"email": "invalid-email",
						"password": "password123"
					}
					"""))
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
				.content("""
					{
						"name": "사용자",
						"email": "test@test.com",
						"password": "1234567"
					}
					"""))
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
				.content("""
					{
						"name": "   ",
						"email": "test@test.com",
						"password": "password123"
					}
					"""))
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
				.content("""
					{
						"name": "사용자",
						"email": "   ",
						"password": "password123"
					}
					"""))
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
				.content("""
					{
						"name": "사용자",
						"email": "test@test.com",
						"password": "   "
					}
					"""))
			.andExpect(status().isBadRequest());

		verifyNoInteractions(userService);
	}

	@Test
	@DisplayName("관리자 권한 수정 요청이 유효하면 204 No Content를 반환한다")
	void updateRole_returnNoContent_whenRequestIsValid() throws Exception {
		// given
		UUID userId = UUID.randomUUID();

		// when & then
		mockMvc.perform(patch("/api/users/{userId}/role", userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
						"role": "ADMIN"
					}
					"""))
			.andExpect(status().isNoContent());

		verify(userAdminService).updateRole(userId, new UserRoleUpdateRequest(UserRole.ADMIN));
	}

	@Test
	@DisplayName("관리자 권한 수정 요청에서 권한이 누락되면 400을 반환한다")
	void updateRole_returnBadRequest_whenRoleIsNull() throws Exception {
		// given
		UUID userId = UUID.randomUUID();

		// when & then
		mockMvc.perform(patch("/api/users/{userId}/role", userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
					}
					"""))
			.andExpect(status().isBadRequest());

		verifyNoInteractions(userService);
		verifyNoInteractions(userAdminService);
	}

	@Test
	@DisplayName("관리자 권한 수정 대상 사용자가 없으면 404를 반환한다")
	void updateRole_returnNotFound_whenUserDoesNotExist() throws Exception {
		// given
		UUID userId = UUID.randomUUID();

		willThrow(new UserException(
				UserErrorCode.USER_NOT_FOUND,
				Map.of("userId", userId)
			))
			.given(userAdminService)
			.updateRole(userId, new UserRoleUpdateRequest(UserRole.ADMIN));

		// when & then
		mockMvc.perform(patch("/api/users/{userId}/role", userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
						"role": "ADMIN"
					}
					"""))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.exceptionName").value("UserException"))
			.andExpect(jsonPath("$.message").value("사용자를 찾을 수 없습니다."))
			.andExpect(jsonPath("$.details.userId").value(userId.toString()));
	}
}
