package com.team04.mopl.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team04.mopl.auth.security.MoplUserDetails;
import com.team04.mopl.auth.security.filter.JwtAuthenticationFilter;
import com.team04.mopl.common.enums.SortDirection;
import com.team04.mopl.common.exception.GlobalExceptionHandler;
import com.team04.mopl.user.dto.request.ChangePasswordRequest;
import com.team04.mopl.user.dto.request.UserCreateRequest;
import com.team04.mopl.user.dto.request.UserLockUpdateRequest;
import com.team04.mopl.user.dto.request.UserPageRequest;
import com.team04.mopl.user.dto.request.UserRoleUpdateRequest;
import com.team04.mopl.user.dto.request.UserUpdateRequest;
import com.team04.mopl.user.dto.response.CursorResponseUserDto;
import com.team04.mopl.user.dto.response.UserDto;
import com.team04.mopl.user.entity.UserRole;
import com.team04.mopl.user.enums.UserSortBy;
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

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private UserService userService;

	@MockitoBean
	private UserAdminService userAdminService;

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

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
	@DisplayName("사용자 상세 조회 요청이 유효하면 200과 사용자 정보를 반환한다")
	void findById_returnUser_whenUserExists() throws Exception {
		// given
		UUID userId = UUID.randomUUID();
		Instant createdAt = Instant.parse("2026-07-07T00:00:00Z");
		UserDto response = new UserDto(
			userId,
			createdAt,
			"test@test.com",
			"사용자",
			"http://localhost:8080/profile-images/profile.png",
			UserRole.USER,
			false
		);

		given(userService.findById(userId))
			.willReturn(response);

		// when & then
		mockMvc.perform(get("/api/users/{userId}", userId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(userId.toString()))
			.andExpect(jsonPath("$.createdAt").value(createdAt.toString()))
			.andExpect(jsonPath("$.email").value("test@test.com"))
			.andExpect(jsonPath("$.name").value("사용자"))
			.andExpect(jsonPath("$.profileImageUrl").value("http://localhost:8080/profile-images/profile.png"))
			.andExpect(jsonPath("$.role").value("USER"))
			.andExpect(jsonPath("$.locked").value(false));

		verify(userService).findById(userId);
	}

	@Test
	@DisplayName("사용자 상세 조회 대상 사용자가 없으면 404를 반환한다")
	void findById_returnNotFound_whenUserDoesNotExist() throws Exception {
		// given
		UUID userId = UUID.randomUUID();

		willThrow(new UserException(
				UserErrorCode.USER_NOT_FOUND,
				Map.of("userId", userId)
			))
			.given(userService)
			.findById(userId);

		// when & then
		mockMvc.perform(get("/api/users/{userId}", userId))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.exceptionName").value("UserException"))
			.andExpect(jsonPath("$.message").value("사용자를 찾을 수 없습니다."))
			.andExpect(jsonPath("$.details.userId").value(userId.toString()));
	}

	@Test
	@DisplayName("사용자 상세 조회 요청에서 userId가 UUID 형식이 아니면 400을 반환한다")
	void findById_returnBadRequest_whenUserIdIsInvalid() throws Exception {
		// given

		// when & then
		mockMvc.perform(get("/api/users/{userId}", "invalid-user-id"))
			.andExpect(status().isBadRequest());

		verifyNoInteractions(userService);
	}

	@Test
	@DisplayName("프로필 변경 요청이 유효하면 200과 변경된 사용자 정보를 반환한다")
	void updateProfile_returnUpdatedUser_whenRequestIsValid() throws Exception {
		// given
		UUID userId = UUID.randomUUID();
		Instant createdAt = Instant.parse("2026-07-06T00:00:00Z");
		UserUpdateRequest request = new UserUpdateRequest("새이름");
		UserDto response = new UserDto(
			userId,
			createdAt,
			"test@test.com",
			"새이름",
			"http://localhost:8080/profile-images/profile.png",
			UserRole.USER,
			false
		);
		MockMultipartFile requestPart = createRequestPart(request);
		MockMultipartFile image = new MockMultipartFile(
			"image",
			"profile.png",
			MediaType.IMAGE_PNG_VALUE,
			"image-data".getBytes()
		);
		MoplUserDetails userDetails = MoplUserDetails.authenticated(userId, "test@test.com", UserRole.USER);
		mockSecurityContext(userDetails);

		given(userService.updateProfile(eq(userId), eq(request), any(MultipartFile.class), eq(userId)))
			.willReturn(response);

		// when & then
		mockMvc.perform(multipart("/api/users/{userId}", userId)
				.file(requestPart)
				.file(image)
				.with(req -> {
					req.setMethod("PATCH");
					return req;
				}))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(userId.toString()))
			.andExpect(jsonPath("$.email").value("test@test.com"))
			.andExpect(jsonPath("$.name").value("새이름"))
			.andExpect(jsonPath("$.profileImageUrl").value("http://localhost:8080/profile-images/profile.png"))
			.andExpect(jsonPath("$.role").value("USER"))
			.andExpect(jsonPath("$.locked").value(false));

		verify(userService).updateProfile(eq(userId), eq(request), any(MultipartFile.class), eq(userId));
	}

	@Test
	@DisplayName("프로필 변경 요청에서 이미지만 있으면 200과 변경된 사용자 정보를 반환한다")
	void updateProfile_returnUpdatedUser_whenOnlyImageIsProvided() throws Exception {
		// given
		UUID userId = UUID.randomUUID();
		Instant createdAt = Instant.parse("2026-07-06T00:00:00Z");
		UserUpdateRequest request = new UserUpdateRequest(null);
		UserDto response = new UserDto(
			userId,
			createdAt,
			"test@test.com",
			"기존이름",
			"http://localhost:8080/profile-images/profile.png",
			UserRole.USER,
			false
		);
		MockMultipartFile requestPart = createRequestPart(request);
		MockMultipartFile image = new MockMultipartFile(
			"image",
			"profile.png",
			MediaType.IMAGE_PNG_VALUE,
			"image-data".getBytes()
		);
		MoplUserDetails userDetails = MoplUserDetails.authenticated(userId, "test@test.com", UserRole.USER);
		mockSecurityContext(userDetails);

		given(userService.updateProfile(eq(userId), eq(request), any(MultipartFile.class), eq(userId)))
			.willReturn(response);

		// when & then
		mockMvc.perform(multipart("/api/users/{userId}", userId)
				.file(requestPart)
				.file(image)
				.with(req -> {
					req.setMethod("PATCH");
					return req;
				}))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(userId.toString()))
			.andExpect(jsonPath("$.email").value("test@test.com"))
			.andExpect(jsonPath("$.name").value("기존이름"))
			.andExpect(jsonPath("$.profileImageUrl").value("http://localhost:8080/profile-images/profile.png"))
			.andExpect(jsonPath("$.role").value("USER"))
			.andExpect(jsonPath("$.locked").value(false));

		verify(userService).updateProfile(eq(userId), eq(request), any(MultipartFile.class), eq(userId));
	}

	@Test
	@DisplayName("프로필 변경 요청에서 이름이 50자를 초과하면 400을 반환한다")
	void updateProfile_returnBadRequest_whenNameIsTooLong() throws Exception {
		// given
		UUID userId = UUID.randomUUID();
		UserUpdateRequest request = new UserUpdateRequest("가".repeat(51));
		MockMultipartFile requestPart = createRequestPart(request);
		MoplUserDetails userDetails = MoplUserDetails.authenticated(userId, "test@test.com", UserRole.USER);
		mockSecurityContext(userDetails);

		// when & then
		mockMvc.perform(multipart("/api/users/{userId}", userId)
				.file(requestPart)
				.with(req -> {
					req.setMethod("PATCH");
					return req;
				}))
			.andExpect(status().isBadRequest());

		verifyNoInteractions(userService);
	}

	@Test
	@DisplayName("프로필 변경 요청에서 본인이 아니면 403을 반환한다")
	void updateProfile_returnForbidden_whenCurrentUserIsNotOwner() throws Exception {
		// given
		UUID userId = UUID.randomUUID();
		UUID currentUserId = UUID.randomUUID();
		UserUpdateRequest request = new UserUpdateRequest("새이름");
		MockMultipartFile requestPart = createRequestPart(request);
		MoplUserDetails userDetails = MoplUserDetails.authenticated(currentUserId, "test@test.com", UserRole.USER);
		mockSecurityContext(userDetails);

		willThrow(new UserException(
				UserErrorCode.USER_PROFILE_ACCESS_DENIED,
				Map.of("userId", userId, "currentUserId", currentUserId.toString())
			))
			.given(userService)
			.updateProfile(userId, request, null, currentUserId);

		// when & then
		mockMvc.perform(multipart("/api/users/{userId}", userId)
				.file(requestPart)
				.with(req -> {
					req.setMethod("PATCH");
					return req;
				}))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.exceptionName").value("UserException"))
			.andExpect(jsonPath("$.message").value("본인의 프로필만 변경할 수 있습니다."))
			.andExpect(jsonPath("$.details.userId").value(userId.toString()));
	}

	@Test
	@DisplayName("프로필 변경 대상 사용자가 없으면 404를 반환한다")
	void updateProfile_returnNotFound_whenUserDoesNotExist() throws Exception {
		// given
		UUID userId = UUID.randomUUID();
		UserUpdateRequest request = new UserUpdateRequest("새이름");
		MockMultipartFile requestPart = createRequestPart(request);
		MoplUserDetails userDetails = MoplUserDetails.authenticated(userId, "test@test.com", UserRole.USER);
		mockSecurityContext(userDetails);

		willThrow(new UserException(
				UserErrorCode.USER_NOT_FOUND,
				Map.of("userId", userId)
			))
			.given(userService)
			.updateProfile(userId, request, null, userId);

		// when & then
		mockMvc.perform(multipart("/api/users/{userId}", userId)
				.file(requestPart)
				.with(req -> {
					req.setMethod("PATCH");
					return req;
				}))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.exceptionName").value("UserException"))
			.andExpect(jsonPath("$.message").value("사용자를 찾을 수 없습니다."))
			.andExpect(jsonPath("$.details.userId").value(userId.toString()));
	}

	@Test
	@DisplayName("프로필 변경 요청에서 request 파트가 누락되면 400을 반환한다")
	void updateProfile_returnBadRequest_whenRequestPartMissing() throws Exception {
		// given
		UUID userId = UUID.randomUUID();
		MoplUserDetails userDetails = MoplUserDetails.authenticated(userId, "test@test.com", UserRole.USER);
		mockSecurityContext(userDetails);

		// when & then
		mockMvc.perform(multipart("/api/users/{userId}", userId)
				.with(req -> {
					req.setMethod("PATCH");
					return req;
				}))
			.andExpect(status().isBadRequest());

		verifyNoInteractions(userService);
	}

	@Test
	@DisplayName("비밀번호 변경 요청이 유효하면 204 No Content를 반환한다")
	void updatePassword_returnNoContent_whenRequestIsValid() throws Exception {
		// given
		UUID userId = UUID.randomUUID();
		ChangePasswordRequest request = new ChangePasswordRequest("newPassword123");
		MoplUserDetails userDetails = MoplUserDetails.authenticated(userId, "test@test.com", UserRole.USER);
		mockSecurityContext(userDetails);

		// when & then
		mockMvc.perform(patch("/api/users/{userId}/password", userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
						"password": "newPassword123"
					}
					"""))
			.andExpect(status().isNoContent());

		verify(userService).updatePassword(userId, request, userId);
	}

	@Test
	@DisplayName("비밀번호 변경 요청에서 비밀번호가 공백이면 400을 반환한다")
	void updatePassword_returnBadRequest_whenPasswordIsBlank() throws Exception {
		// given
		UUID userId = UUID.randomUUID();
		MoplUserDetails userDetails = MoplUserDetails.authenticated(userId, "test@test.com", UserRole.USER);
		mockSecurityContext(userDetails);

		// when & then
		mockMvc.perform(patch("/api/users/{userId}/password", userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
						"password": "   "
					}
					"""))
			.andExpect(status().isBadRequest());

		verifyNoInteractions(userService);
	}

	@Test
	@DisplayName("비밀번호 변경 요청에서 비밀번호가 8자 미만이면 400을 반환한다")
	void updatePassword_returnBadRequest_whenPasswordIsShorterThanEightCharacters() throws Exception {
		// given
		UUID userId = UUID.randomUUID();
		MoplUserDetails userDetails = MoplUserDetails.authenticated(userId, "test@test.com", UserRole.USER);
		mockSecurityContext(userDetails);

		// when & then
		mockMvc.perform(patch("/api/users/{userId}/password", userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
						"password": "1234567"
					}
					"""))
			.andExpect(status().isBadRequest());

		verifyNoInteractions(userService);
	}

	@Test
	@DisplayName("비밀번호 변경 요청에서 본인이 아니면 403을 반환한다")
	void updatePassword_returnForbidden_whenCurrentUserIsNotOwner() throws Exception {
		// given
		UUID userId = UUID.randomUUID();
		UUID currentUserId = UUID.randomUUID();
		ChangePasswordRequest request = new ChangePasswordRequest("newPassword123");
		MoplUserDetails userDetails = MoplUserDetails.authenticated(currentUserId, "test@test.com", UserRole.USER);
		mockSecurityContext(userDetails);

		willThrow(new UserException(
				UserErrorCode.USER_PASSWORD_ACCESS_DENIED,
				Map.of("userId", userId, "currentUserId", currentUserId.toString())
			))
			.given(userService)
			.updatePassword(userId, request, currentUserId);

		// when & then
		mockMvc.perform(patch("/api/users/{userId}/password", userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
						"password": "newPassword123"
					}
					"""))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.exceptionName").value("UserException"))
			.andExpect(jsonPath("$.message").value("본인의 비밀번호만 변경할 수 있습니다."))
			.andExpect(jsonPath("$.details.userId").value(userId.toString()));
	}

	@Test
	@DisplayName("비밀번호 변경 대상 사용자가 없으면 404를 반환한다")
	void updatePassword_returnNotFound_whenUserDoesNotExist() throws Exception {
		// given
		UUID userId = UUID.randomUUID();
		ChangePasswordRequest request = new ChangePasswordRequest("newPassword123");
		MoplUserDetails userDetails = MoplUserDetails.authenticated(userId, "test@test.com", UserRole.USER);
		mockSecurityContext(userDetails);

		willThrow(new UserException(
				UserErrorCode.USER_NOT_FOUND,
				Map.of("userId", userId)
			))
			.given(userService)
			.updatePassword(userId, request, userId);

		// when & then
		mockMvc.perform(patch("/api/users/{userId}/password", userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
						"password": "newPassword123"
					}
					"""))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.exceptionName").value("UserException"))
			.andExpect(jsonPath("$.message").value("사용자를 찾을 수 없습니다."))
			.andExpect(jsonPath("$.details.userId").value(userId.toString()));
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
	@DisplayName("관리자 계정 잠금 상태 변경 요청이 유효하면 204 No Content를 반환한다")
	void updateLocked_returnNoContent_whenRequestIsValid() throws Exception {
		// given
		UUID userId = UUID.randomUUID();

		// when & then
		mockMvc.perform(patch("/api/users/{userId}/locked", userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
						"locked": true
					}
					"""))
			.andExpect(status().isNoContent());

		verify(userAdminService).updateLocked(userId, new UserLockUpdateRequest(true));
	}

	@Test
	@DisplayName("관리자 계정 잠금 해제 요청이 유효하면 204 No Content를 반환한다")
	void updateLocked_returnNoContent_whenUnlockRequestIsValid() throws Exception {
		// given
		UUID userId = UUID.randomUUID();

		// when & then
		mockMvc.perform(patch("/api/users/{userId}/locked", userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
						"locked": false
					}
					"""))
			.andExpect(status().isNoContent());

		verify(userAdminService).updateLocked(userId, new UserLockUpdateRequest(false));
	}

	@Test
	@DisplayName("관리자 사용자 목록 조회 요청이 유효하면 커서 페이지 응답을 반환한다")
	void findUsers_returnCursorResponse_whenRequestIsValid() throws Exception {
		// given
		UUID userId = UUID.randomUUID();
		Instant createdAt = Instant.parse("2026-07-02T00:00:00Z");
		UserDto user = new UserDto(
			userId,
			createdAt,
			"test@test.com",
			"사용자",
			null,
			UserRole.USER,
			false
		);
		UserPageRequest request = new UserPageRequest(
			"test",
			UserRole.USER,
			false,
			null,
			null,
			20,
			SortDirection.ASCENDING,
			UserSortBy.name
		);
		CursorResponseUserDto response = new CursorResponseUserDto(
			List.of(user),
			null,
			null,
			false,
			1L,
			"name",
			SortDirection.ASCENDING
		);

		given(userAdminService.findUsers(request))
			.willReturn(response);

		// when & then
		mockMvc.perform(get("/api/users")
				.param("emailLike", "test")
				.param("roleEqual", "USER")
				.param("isLocked", "false")
				.param("limit", "20")
				.param("sortDirection", "ASCENDING")
				.param("sortBy", "name"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data[0].id").value(userId.toString()))
			.andExpect(jsonPath("$.data[0].email").value("test@test.com"))
			.andExpect(jsonPath("$.data[0].name").value("사용자"))
			.andExpect(jsonPath("$.data[0].role").value("USER"))
			.andExpect(jsonPath("$.data[0].locked").value(false))
			.andExpect(jsonPath("$.hasNext").value(false))
			.andExpect(jsonPath("$.totalCount").value(1))
			.andExpect(jsonPath("$.sortBy").value("name"))
			.andExpect(jsonPath("$.sortDirection").value("ASCENDING"));

		verify(userAdminService).findUsers(request);
	}

	@Test
	@DisplayName("관리자 사용자 목록 조회 결과에 다음 페이지가 있으면 다음 커서를 반환한다")
	void findUsers_returnNextCursor_whenHasNextIsTrue() throws Exception {
		// given
		UUID userId = UUID.randomUUID();
		Instant createdAt = Instant.parse("2026-07-02T00:00:00Z");
		UserDto user = new UserDto(
			userId,
			createdAt,
			"admin@test.com",
			"관리자",
			"https://example.com/admin.png",
			UserRole.ADMIN,
			true
		);
		UserPageRequest request = new UserPageRequest(
			null,
			null,
			null,
			null,
			null,
			1,
			SortDirection.DESCENDING,
			UserSortBy.createdAt
		);
		CursorResponseUserDto response = new CursorResponseUserDto(
			List.of(user),
			createdAt.toString(),
			userId,
			true,
			2L,
			"createdAt",
			SortDirection.DESCENDING
		);

		given(userAdminService.findUsers(request))
			.willReturn(response);

		// when & then
		mockMvc.perform(get("/api/users")
				.param("limit", "1")
				.param("sortDirection", "DESCENDING")
				.param("sortBy", "createdAt"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data[0].id").value(userId.toString()))
			.andExpect(jsonPath("$.data[0].profileImageUrl").value("https://example.com/admin.png"))
			.andExpect(jsonPath("$.data[0].role").value("ADMIN"))
			.andExpect(jsonPath("$.data[0].locked").value(true))
			.andExpect(jsonPath("$.nextCursor").value(createdAt.toString()))
			.andExpect(jsonPath("$.nextIdAfter").value(userId.toString()))
			.andExpect(jsonPath("$.hasNext").value(true))
			.andExpect(jsonPath("$.totalCount").value(2))
			.andExpect(jsonPath("$.sortBy").value("createdAt"))
			.andExpect(jsonPath("$.sortDirection").value("DESCENDING"));

		verify(userAdminService).findUsers(request);
	}

	@Test
	@DisplayName("관리자 사용자 목록 조회 요청에서 필수 파라미터가 누락되면 400을 반환한다")
	void findUsers_returnBadRequest_whenRequiredParameterMissing() throws Exception {
		// given

		// when & then
		mockMvc.perform(get("/api/users")
				.param("limit", "20")
				.param("sortDirection", "ASCENDING"))
			.andExpect(status().isBadRequest());

		verifyNoInteractions(userAdminService);
	}

	@Test
	@DisplayName("관리자 사용자 목록 조회 요청에서 커서와 보조 커서가 함께 없지 않으면 400을 반환한다")
	void findUsers_returnBadRequest_whenCursorAndIdAfterNotPaired() throws Exception {
		// given

		// when & then
		mockMvc.perform(get("/api/users")
				.param("cursor", "Alpha")
				.param("limit", "20")
				.param("sortDirection", "ASCENDING")
				.param("sortBy", "name"))
			.andExpect(status().isBadRequest());

		verifyNoInteractions(userAdminService);
	}

	@Test
	@DisplayName("관리자 사용자 목록 조회 요청에서 보조 커서만 있으면 400을 반환한다")
	void findUsers_returnBadRequest_whenOnlyIdAfterExists() throws Exception {
		// given
		UUID idAfter = UUID.randomUUID();

		// when & then
		mockMvc.perform(get("/api/users")
				.param("idAfter", idAfter.toString())
				.param("limit", "20")
				.param("sortDirection", "ASCENDING")
				.param("sortBy", "name"))
			.andExpect(status().isBadRequest());

		verifyNoInteractions(userAdminService);
	}

	@Test
	@DisplayName("관리자 사용자 목록 조회 요청에서 limit이 범위를 벗어나면 400을 반환한다")
	void findUsers_returnBadRequest_whenLimitOutOfRange() throws Exception {
		// given

		// when & then
		mockMvc.perform(get("/api/users")
				.param("limit", "101")
				.param("sortDirection", "ASCENDING")
				.param("sortBy", "name"))
			.andExpect(status().isBadRequest());

		verifyNoInteractions(userAdminService);
	}

	@Test
	@DisplayName("관리자 사용자 목록 조회 요청에서 limit이 1보다 작으면 400을 반환한다")
	void findUsers_returnBadRequest_whenLimitIsLessThanOne() throws Exception {
		// given

		// when & then
		mockMvc.perform(get("/api/users")
				.param("limit", "0")
				.param("sortDirection", "ASCENDING")
				.param("sortBy", "name"))
			.andExpect(status().isBadRequest());

		verifyNoInteractions(userAdminService);
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
	@DisplayName("관리자 계정 잠금 상태 변경 요청에서 잠금 상태가 누락되면 400을 반환한다")
	void updateLocked_returnBadRequest_whenLockedIsNull() throws Exception {
		// given
		UUID userId = UUID.randomUUID();

		// when & then
		mockMvc.perform(patch("/api/users/{userId}/locked", userId)
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

	@Test
	@DisplayName("관리자 계정 잠금 상태 변경 대상 사용자가 없으면 404를 반환한다")
	void updateLocked_returnNotFound_whenUserDoesNotExist() throws Exception {
		// given
		UUID userId = UUID.randomUUID();

		willThrow(new UserException(
				UserErrorCode.USER_NOT_FOUND,
				Map.of("userId", userId)
			))
			.given(userAdminService)
			.updateLocked(userId, new UserLockUpdateRequest(true));

		// when & then
		mockMvc.perform(patch("/api/users/{userId}/locked", userId)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
						"locked": true
					}
					"""))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.exceptionName").value("UserException"))
			.andExpect(jsonPath("$.message").value("사용자를 찾을 수 없습니다."))
			.andExpect(jsonPath("$.details.userId").value(userId.toString()));
	}

	private MockMultipartFile createRequestPart(UserUpdateRequest request) throws Exception {
		return new MockMultipartFile(
			"request",
			"",
			MediaType.APPLICATION_JSON_VALUE,
			objectMapper.writeValueAsBytes(request)
		);
	}

	private void mockSecurityContext(MoplUserDetails userDetails) {
		SecurityContextHolder.getContext().setAuthentication(createAuthentication(userDetails));
	}

	private UsernamePasswordAuthenticationToken createAuthentication(MoplUserDetails userDetails) {
		return new UsernamePasswordAuthenticationToken(
			userDetails,
			null,
			userDetails.getAuthorities()
		);
	}
}
