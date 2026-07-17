package com.team04.mopl.user.integration;

import static com.team04.mopl.support.ErrorResponseAssertions.assertErrorResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team04.mopl.auth.exception.AuthErrorCode;
import com.team04.mopl.auth.session.AuthSessionStore;
import com.team04.mopl.support.IntegrationTestBase;
import com.team04.mopl.user.entity.User;
import com.team04.mopl.user.entity.UserRole;
import com.team04.mopl.user.exception.UserErrorCode;
import com.team04.mopl.user.repository.UserRepository;

import jakarta.persistence.EntityManager;

@Transactional
class UserIntegrationTest extends IntegrationTestBase {

	private static final String DEFAULT_PASSWORD = "password123";
	private static final String CHANGED_PASSWORD = "changedPassword123";

	private final Set<UUID> createdUserIds = new HashSet<>();

	private MockMvc mockMvc;

	@Autowired
	private WebApplicationContext webApplicationContext;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private AuthSessionStore authSessionStore;

	@BeforeEach
	void setUpMockMvc() {
		mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
			.apply(SecurityMockMvcConfigurers.springSecurity())
			.build();
	}

	@AfterEach
	void tearDown() {
		createdUserIds.forEach(authSessionStore::deleteByUserId);
	}

	@Test
	@DisplayName("회원가입하면 기본 USER 권한의 잠기지 않은 사용자를 저장한다")
	void createUser_saveDefaultUser_whenRequestIsValid() throws Exception {
		// given
		String email = uniqueEmail("signup");

		// when
		mockMvc.perform(post("/api/users")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
						"name": "가입사용자",
						"email": "%s",
						"password": "%s"
					}
					""".formatted(email, DEFAULT_PASSWORD)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.email").value(email))
			.andExpect(jsonPath("$.name").value("가입사용자"))
			.andExpect(jsonPath("$.role").value("USER"))
			.andExpect(jsonPath("$.locked").value(false));

		// then
		User savedUser = userRepository.findByEmail(email).orElseThrow();

		assertThat(savedUser.getRole()).isEqualTo(UserRole.USER);
		assertThat(savedUser.isLocked()).isFalse();
		assertThat(savedUser.getPasswordHashForAuthentication()).isNotEqualTo(DEFAULT_PASSWORD);
		assertThat(passwordEncoder.matches(DEFAULT_PASSWORD, savedUser.getPasswordHashForAuthentication())).isTrue();
	}

	@Test
	@DisplayName("일반 사용자는 관리자 사용자 목록을 조회할 수 없다")
	void findUsers_returnForbidden_whenRequesterIsNotAdmin() throws Exception {
		// given
		User user = createUser("일반사용자", uniqueEmail("normal"), DEFAULT_PASSWORD, UserRole.USER, false);
		String accessToken = accessTokenFrom(signIn(user.getEmail(), DEFAULT_PASSWORD).andReturn());

		// when & then
		assertErrorResponse(
			mockMvc.perform(get("/api/users")
				.header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
				.param("limit", "20")
				.param("sortDirection", "ASCENDING")
				.param("sortBy", "email")),
			HttpStatus.FORBIDDEN,
			"AuthException",
			AuthErrorCode.AUTH_ACCESS_DENIED.getMessage()
		);
	}

	@Test
	@DisplayName("관리자는 사용자 목록을 필터와 커서 응답 형식으로 조회할 수 있다")
	void findUsers_returnFilteredCursorResponse_whenRequesterIsAdmin() throws Exception {
		// given
		User admin = createUser("관리자", uniqueEmail("admin"), DEFAULT_PASSWORD, UserRole.ADMIN, false);
		User target = createUser("대상사용자", uniqueEmail("target-list"), DEFAULT_PASSWORD, UserRole.USER, false);
		createUser("잠긴사용자", uniqueEmail("target-list"), DEFAULT_PASSWORD, UserRole.USER, true);
		String accessToken = accessTokenFrom(signIn(admin.getEmail(), DEFAULT_PASSWORD).andReturn());

		// when & then
		mockMvc.perform(get("/api/users")
				.header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
				.param("emailLike", "target-list")
				.param("roleEqual", "USER")
				.param("isLocked", "false")
				.param("limit", "20")
				.param("sortDirection", "ASCENDING")
				.param("sortBy", "email"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.length()").value(1))
			.andExpect(jsonPath("$.data[0].id").value(target.getId().toString()))
			.andExpect(jsonPath("$.data[0].email").value(target.getEmail()))
			.andExpect(jsonPath("$.data[0].role").value("USER"))
			.andExpect(jsonPath("$.data[0].locked").value(false))
			.andExpect(jsonPath("$.hasNext").value(false))
			.andExpect(jsonPath("$.totalCount").value(1))
			.andExpect(jsonPath("$.sortBy").value("email"))
			.andExpect(jsonPath("$.sortDirection").value("ASCENDING"));
	}

	@Test
	@DisplayName("관리자가 권한을 변경하면 DB 권한이 바뀌고 대상 사용자의 기존 인증 세션이 무효화된다")
	void updateRole_changeRoleAndInvalidateTargetSession_whenRequesterIsAdmin() throws Exception {
		// given
		User admin = createUser("권한관리자", uniqueEmail("role-admin"), DEFAULT_PASSWORD, UserRole.ADMIN, false);
		User target = createUser("권한대상", uniqueEmail("role-target"), DEFAULT_PASSWORD, UserRole.USER, false);
		String targetAccessToken = accessTokenFrom(signIn(target.getEmail(), DEFAULT_PASSWORD).andReturn());
		String adminAccessToken = accessTokenFrom(signIn(admin.getEmail(), DEFAULT_PASSWORD).andReturn());

		// when
		mockMvc.perform(patch("/api/users/{userId}/role", target.getId())
				.with(csrf())
				.header(HttpHeaders.AUTHORIZATION, bearer(adminAccessToken))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
						"role": "ADMIN"
					}
					"""))
			.andExpect(status().isNoContent());

		// then
		flushAndClear();
		User updatedTarget = userRepository.findById(target.getId()).orElseThrow();

		assertThat(updatedTarget.getRole()).isEqualTo(UserRole.ADMIN);
		assertThat(authSessionStore.findByUserId(target.getId())).isEmpty();

		assertErrorResponse(
			mockMvc.perform(get("/api/users/{userId}", target.getId())
				.header(HttpHeaders.AUTHORIZATION, bearer(targetAccessToken))),
			HttpStatus.UNAUTHORIZED,
			"AuthException",
			AuthErrorCode.AUTH_SESSION_INVALID.getMessage()
		);
	}

	@Test
	@DisplayName("일반 사용자가 다른 사용자의 권한 변경을 요청하면 403을 반환하고 대상 상태를 유지한다")
	void updateRole_returnForbiddenAndPreserveTarget_whenRequesterIsNotAdmin() throws Exception {
		// given
		User requester = createUser("권한변경요청자", uniqueEmail("role-requester"),
			DEFAULT_PASSWORD, UserRole.USER, false);
		User target = createUser("권한변경대상", uniqueEmail("role-denied-target"),
			DEFAULT_PASSWORD, UserRole.USER, false);
		String targetAccessToken = accessTokenFrom(signIn(target.getEmail(), DEFAULT_PASSWORD).andReturn());
		String requesterAccessToken = accessTokenFrom(signIn(requester.getEmail(), DEFAULT_PASSWORD).andReturn());

		// when & then
		assertErrorResponse(
			mockMvc.perform(patch("/api/users/{userId}/role", target.getId())
				.with(csrf())
				.header(HttpHeaders.AUTHORIZATION, bearer(requesterAccessToken))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
						"role": "ADMIN"
					}
					""")),
			HttpStatus.FORBIDDEN,
			"AuthException",
			AuthErrorCode.AUTH_ACCESS_DENIED.getMessage()
		);

		flushAndClear();
		User unchangedTarget = userRepository.findById(target.getId()).orElseThrow();

		assertThat(unchangedTarget.getRole()).isEqualTo(UserRole.USER);
		assertThat(authSessionStore.findByUserId(target.getId())).isPresent();

		mockMvc.perform(get("/api/users/{userId}", target.getId())
				.header(HttpHeaders.AUTHORIZATION, bearer(targetAccessToken)))
			.andExpect(status().isOk());
	}

	@Test
	@DisplayName("관리자가 계정을 잠그면 DB 잠금 상태가 바뀌고 기존 세션과 이후 로그인을 막는다")
	void updateLocked_lockUserInvalidateSessionAndRejectSignIn_whenRequesterIsAdmin() throws Exception {
		// given
		User admin = createUser("잠금관리자", uniqueEmail("lock-admin"), DEFAULT_PASSWORD, UserRole.ADMIN, false);
		User target = createUser("잠금대상", uniqueEmail("lock-target"), DEFAULT_PASSWORD, UserRole.USER, false);
		String targetAccessToken = accessTokenFrom(signIn(target.getEmail(), DEFAULT_PASSWORD).andReturn());
		String adminAccessToken = accessTokenFrom(signIn(admin.getEmail(), DEFAULT_PASSWORD).andReturn());

		// when
		mockMvc.perform(patch("/api/users/{userId}/locked", target.getId())
				.with(csrf())
				.header(HttpHeaders.AUTHORIZATION, bearer(adminAccessToken))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
						"locked": true
					}
					"""))
			.andExpect(status().isNoContent());

		// then
		flushAndClear();
		User lockedTarget = userRepository.findById(target.getId()).orElseThrow();

		assertThat(lockedTarget.isLocked()).isTrue();
		assertThat(authSessionStore.findByUserId(target.getId())).isEmpty();

		assertErrorResponse(
			mockMvc.perform(get("/api/users/{userId}", target.getId())
				.header(HttpHeaders.AUTHORIZATION, bearer(targetAccessToken))),
			HttpStatus.UNAUTHORIZED,
			"AuthException",
			AuthErrorCode.AUTH_SESSION_INVALID.getMessage()
		);

		assertErrorResponse(
			signIn(target.getEmail(), DEFAULT_PASSWORD),
			HttpStatus.UNAUTHORIZED,
			"AuthException",
			AuthErrorCode.AUTH_LOCKED_ACCOUNT.getMessage()
		);
	}

	@Test
	@DisplayName("일반 사용자가 다른 사용자의 계정 잠금을 요청하면 403을 반환하고 대상 상태를 유지한다")
	void updateLocked_returnForbiddenAndPreserveTarget_whenRequesterIsNotAdmin() throws Exception {
		// given
		User requester = createUser("잠금요청자", uniqueEmail("lock-requester"),
			DEFAULT_PASSWORD, UserRole.USER, false);
		User target = createUser("잠금대상", uniqueEmail("lock-denied-target"),
			DEFAULT_PASSWORD, UserRole.USER, false);
		String targetAccessToken = accessTokenFrom(signIn(target.getEmail(), DEFAULT_PASSWORD).andReturn());
		String requesterAccessToken = accessTokenFrom(signIn(requester.getEmail(), DEFAULT_PASSWORD).andReturn());

		// when & then
		assertErrorResponse(
			mockMvc.perform(patch("/api/users/{userId}/locked", target.getId())
				.with(csrf())
				.header(HttpHeaders.AUTHORIZATION, bearer(requesterAccessToken))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
						"locked": true
					}
					""")),
			HttpStatus.FORBIDDEN,
			"AuthException",
			AuthErrorCode.AUTH_ACCESS_DENIED.getMessage()
		);

		flushAndClear();
		User unchangedTarget = userRepository.findById(target.getId()).orElseThrow();

		assertThat(unchangedTarget.isLocked()).isFalse();
		assertThat(authSessionStore.findByUserId(target.getId())).isPresent();

		mockMvc.perform(get("/api/users/{userId}", target.getId())
				.header(HttpHeaders.AUTHORIZATION, bearer(targetAccessToken)))
			.andExpect(status().isOk());
	}

	@Test
	@DisplayName("본인이 비밀번호를 변경하면 기존 세션이 무효화되고 새 비밀번호로만 로그인할 수 있다")
	void updatePassword_changePasswordAndInvalidateSession_whenRequesterIsOwner() throws Exception {
		// given
		User user = createUser("비밀번호사용자", uniqueEmail("password"), DEFAULT_PASSWORD, UserRole.USER, false);
		String accessToken = accessTokenFrom(signIn(user.getEmail(), DEFAULT_PASSWORD).andReturn());

		// when
		mockMvc.perform(patch("/api/users/{userId}/password", user.getId())
				.with(csrf())
				.header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
						"password": "%s"
					}
					""".formatted(CHANGED_PASSWORD)))
			.andExpect(status().isNoContent());

		// then
		flushAndClear();
		User updatedUser = userRepository.findById(user.getId()).orElseThrow();

		assertThat(passwordEncoder.matches(CHANGED_PASSWORD, updatedUser.getPasswordHashForAuthentication())).isTrue();
		assertThat(authSessionStore.findByUserId(user.getId())).isEmpty();

		assertErrorResponse(
			mockMvc.perform(get("/api/users/{userId}", user.getId())
				.header(HttpHeaders.AUTHORIZATION, bearer(accessToken))),
			HttpStatus.UNAUTHORIZED,
			"AuthException",
			AuthErrorCode.AUTH_SESSION_INVALID.getMessage()
		);

		assertErrorResponse(
			signIn(user.getEmail(), DEFAULT_PASSWORD),
			HttpStatus.UNAUTHORIZED,
			"AuthException",
			AuthErrorCode.AUTH_INVALID_CREDENTIALS.getMessage()
		);

		signIn(user.getEmail(), CHANGED_PASSWORD)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.userDto.id").value(user.getId().toString()));
	}

	@Test
	@DisplayName("다른 사용자의 비밀번호 변경을 요청하면 403을 반환하고 비밀번호와 세션을 유지한다")
	void updatePassword_returnForbiddenAndPreserveTarget_whenRequesterIsNotOwner() throws Exception {
		// given
		User requester = createUser("비밀번호변경요청자", uniqueEmail("password-requester"),
			DEFAULT_PASSWORD, UserRole.USER, false);
		User target = createUser("비밀번호변경대상", uniqueEmail("password-denied-target"),
			DEFAULT_PASSWORD, UserRole.USER, false);
		String targetAccessToken = accessTokenFrom(signIn(target.getEmail(), DEFAULT_PASSWORD).andReturn());
		String requesterAccessToken = accessTokenFrom(signIn(requester.getEmail(), DEFAULT_PASSWORD).andReturn());

		// when & then
		assertErrorResponse(
			mockMvc.perform(patch("/api/users/{userId}/password", target.getId())
				.with(csrf())
				.header(HttpHeaders.AUTHORIZATION, bearer(requesterAccessToken))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
						"password": "%s"
					}
					""".formatted(CHANGED_PASSWORD))),
			HttpStatus.FORBIDDEN,
			"UserException",
			UserErrorCode.USER_PASSWORD_ACCESS_DENIED.getMessage()
		)
			.andExpect(jsonPath("$.details.userId").value(target.getId().toString()))
			.andExpect(jsonPath("$.details.currentUserId").value(requester.getId().toString()));

		flushAndClear();
		User unchangedTarget = userRepository.findById(target.getId()).orElseThrow();

		assertThat(passwordEncoder.matches(DEFAULT_PASSWORD,
			unchangedTarget.getPasswordHashForAuthentication())).isTrue();
		assertThat(passwordEncoder.matches(CHANGED_PASSWORD,
			unchangedTarget.getPasswordHashForAuthentication())).isFalse();
		assertThat(authSessionStore.findByUserId(target.getId())).isPresent();

		mockMvc.perform(get("/api/users/{userId}", target.getId())
				.header(HttpHeaders.AUTHORIZATION, bearer(targetAccessToken)))
			.andExpect(status().isOk());
	}

	private ResultActions signIn(
		String email,
		String password
	) throws Exception {
		return mockMvc.perform(post("/api/auth/sign-in")
			.with(csrf())
			.contentType(MediaType.APPLICATION_FORM_URLENCODED)
			.param("username", email)
			.param("password", password));
	}

	private User createUser(
		String name,
		String email,
		String password,
		UserRole role,
		boolean locked
	) {
		User user = User.builder()
			.name(name)
			.email(email)
			.passwordHash(passwordEncoder.encode(password))
			.role(role)
			.locked(locked)
			.build();

		User savedUser = userRepository.saveAndFlush(user);
		createdUserIds.add(savedUser.getId());

		return savedUser;
	}

	private void flushAndClear() {
		entityManager.flush();
		entityManager.clear();
	}

	private String accessTokenFrom(MvcResult result) throws Exception {
		JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());

		return response.get("accessToken").asText();
	}

	private String bearer(String accessToken) {
		return "Bearer " + accessToken;
	}

	private String uniqueEmail(String prefix) {
		return prefix + "-" + UUID.randomUUID() + "@test.com";
	}
}
