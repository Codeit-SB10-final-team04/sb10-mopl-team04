package com.team04.mopl.auth.integration;

import static com.team04.mopl.support.ErrorResponseAssertions.assertErrorResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashSet;
import java.util.List;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team04.mopl.auth.exception.AuthErrorCode;
import com.team04.mopl.auth.repository.TemporaryPasswordRepository;
import com.team04.mopl.auth.service.TemporaryPasswordGenerator;
import com.team04.mopl.auth.session.AuthSessionStore;
import com.team04.mopl.support.IntegrationTestBase;
import com.team04.mopl.user.entity.User;
import com.team04.mopl.user.entity.UserRole;
import com.team04.mopl.user.repository.UserRepository;

import jakarta.servlet.http.Cookie;

@Transactional
class AuthIntegrationTest extends IntegrationTestBase {

	private static final String REFRESH_TOKEN_COOKIE_NAME = "REFRESH_TOKEN";
	private static final String DEFAULT_PASSWORD = "password123";
	private static final String TEMPORARY_PASSWORD = "tempPassword123";

	private final Set<UUID> createdUserIds = new HashSet<>();

	private MockMvc mockMvc;

	@Autowired
	private WebApplicationContext webApplicationContext;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private TemporaryPasswordRepository temporaryPasswordRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private AuthSessionStore authSessionStore;

	@MockitoBean
	private TemporaryPasswordGenerator temporaryPasswordGenerator;

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
	@DisplayName("로그인에 성공하면 access token과 refresh token 쿠키를 발급하고 인증 세션을 저장한다")
	void signIn_issueTokensAndSaveAuthSession_whenCredentialsAreValid() throws Exception {
		// given
		User user = createUser("로그인사용자", uniqueEmail("login"), DEFAULT_PASSWORD, UserRole.USER, false);

		// when
		MvcResult result = signIn(user.getEmail(), DEFAULT_PASSWORD)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.userDto.id").value(user.getId().toString()))
			.andExpect(jsonPath("$.userDto.email").value(user.getEmail()))
			.andExpect(jsonPath("$.userDto.role").value("USER"))
			.andExpect(jsonPath("$.accessToken").isNotEmpty())
			.andReturn();

		// then
		assertThat(accessTokenFrom(result)).isNotBlank();
		assertThat(refreshTokenFrom(result)).isNotBlank();
		assertThat(setCookieHeaders(result))
			.anySatisfy(header -> assertThat(header)
				.contains(REFRESH_TOKEN_COOKIE_NAME + "=")
				.contains("HttpOnly")
				.contains("SameSite=Lax"));
		assertThat(authSessionStore.findByUserId(user.getId())).isPresent();
	}

	@Test
	@DisplayName("잘못된 비밀번호로 로그인하면 401을 반환하고 인증 세션을 만들지 않는다")
	void signIn_returnUnauthorized_whenPasswordIsInvalid() throws Exception {
		// given
		User user = createUser("로그인실패사용자", uniqueEmail("invalid-password"), DEFAULT_PASSWORD, UserRole.USER, false);

		// when & then
		assertErrorResponse(
			signIn(user.getEmail(), "wrongPassword123"),
			HttpStatus.UNAUTHORIZED,
			"AuthException",
			AuthErrorCode.AUTH_INVALID_CREDENTIALS.getMessage()
		);

		assertThat(authSessionStore.findByUserId(user.getId())).isEmpty();
	}

	@Test
	@DisplayName("잠긴 계정으로 로그인하면 401을 반환하고 인증 세션을 만들지 않는다")
	void signIn_returnUnauthorized_whenUserIsLocked() throws Exception {
		// given
		User user = createUser("잠긴사용자", uniqueEmail("locked"), DEFAULT_PASSWORD, UserRole.USER, true);

		// when & then
		assertErrorResponse(
			signIn(user.getEmail(), DEFAULT_PASSWORD),
			HttpStatus.UNAUTHORIZED,
			"AuthException",
			AuthErrorCode.AUTH_LOCKED_ACCOUNT.getMessage()
		);

		assertThat(authSessionStore.findByUserId(user.getId())).isEmpty();
	}

	@Test
	@DisplayName("보호 API는 access token이 없으면 401을 반환하고 유효한 access token이 있으면 접근할 수 있다")
	void protectedApi_requireValidAccessToken() throws Exception {
		// given
		User user = createUser("인증사용자", uniqueEmail("protected"), DEFAULT_PASSWORD, UserRole.USER, false);
		String accessToken = accessTokenFrom(signIn(user.getEmail(), DEFAULT_PASSWORD).andReturn());

		// when & then
		assertErrorResponse(
			mockMvc.perform(get("/api/users/{userId}", user.getId())),
			HttpStatus.UNAUTHORIZED,
			"AuthException",
			AuthErrorCode.AUTH_UNAUTHORIZED.getMessage()
		);

		mockMvc.perform(get("/api/users/{userId}", user.getId())
				.header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(user.getId().toString()))
			.andExpect(jsonPath("$.email").value(user.getEmail()));
	}

	@Test
	@DisplayName("같은 계정이 다시 로그인하면 이전 access token은 인증 세션 불일치로 거부된다")
	void signIn_invalidatePreviousAccessToken_whenSameUserSignsInAgain() throws Exception {
		// given
		User user = createUser("재로그인사용자", uniqueEmail("relogin"), DEFAULT_PASSWORD, UserRole.USER, false);
		String oldAccessToken = accessTokenFrom(signIn(user.getEmail(), DEFAULT_PASSWORD).andReturn());

		// when
		String newAccessToken = accessTokenFrom(signIn(user.getEmail(), DEFAULT_PASSWORD).andReturn());

		// then
		assertErrorResponse(
			mockMvc.perform(get("/api/users/{userId}", user.getId())
				.header(HttpHeaders.AUTHORIZATION, bearer(oldAccessToken))),
			HttpStatus.UNAUTHORIZED,
			"AuthException",
			AuthErrorCode.AUTH_SESSION_INVALID.getMessage()
		);

		mockMvc.perform(get("/api/users/{userId}", user.getId())
				.header(HttpHeaders.AUTHORIZATION, bearer(newAccessToken)))
			.andExpect(status().isOk());
	}

	@Test
	@DisplayName("로그아웃하면 refresh token 쿠키를 만료시키고 현재 인증 세션을 삭제한다")
	void signOut_expireRefreshTokenCookieAndInvalidateAccessToken() throws Exception {
		// given
		User user = createUser("로그아웃사용자", uniqueEmail("logout"), DEFAULT_PASSWORD, UserRole.USER, false);
		String accessToken = accessTokenFrom(signIn(user.getEmail(), DEFAULT_PASSWORD).andReturn());

		// when
		MvcResult signOutResult = mockMvc.perform(post("/api/auth/sign-out")
				.servletPath("/api/auth/sign-out")
				.with(csrf())
				.header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
			.andExpect(status().isNoContent())
			.andReturn();

		// then
		assertThat(setCookieHeaders(signOutResult))
			.anySatisfy(header -> assertThat(header)
				.contains(REFRESH_TOKEN_COOKIE_NAME + "=")
				.contains("Max-Age=0"));
		assertThat(authSessionStore.findByUserId(user.getId())).isEmpty();

		assertErrorResponse(
			mockMvc.perform(get("/api/users/{userId}", user.getId())
				.header(HttpHeaders.AUTHORIZATION, bearer(accessToken))),
			HttpStatus.UNAUTHORIZED,
			"AuthException",
			AuthErrorCode.AUTH_SESSION_INVALID.getMessage()
		);
	}

	@Test
	@DisplayName("refresh token으로 access token과 refresh token을 재발급하고 이전 refresh token은 재사용할 수 없다")
	void refresh_rotateRefreshTokenAndRejectOldRefreshToken() throws Exception {
		// given
		User user = createUser("재발급사용자", uniqueEmail("refresh"), DEFAULT_PASSWORD, UserRole.USER, false);
		String oldRefreshToken = refreshTokenFrom(signIn(user.getEmail(), DEFAULT_PASSWORD).andReturn());

		// when
		MvcResult refreshResult = mockMvc.perform(post("/api/auth/refresh")
				.with(csrf())
				.cookie(new Cookie(REFRESH_TOKEN_COOKIE_NAME, oldRefreshToken)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.userDto.id").value(user.getId().toString()))
			.andExpect(jsonPath("$.accessToken").isNotEmpty())
			.andReturn();

		// then
		String newAccessToken = accessTokenFrom(refreshResult);
		String newRefreshToken = refreshTokenFrom(refreshResult);

		assertThat(newRefreshToken).isNotBlank().isNotEqualTo(oldRefreshToken);

		assertErrorResponse(
			mockMvc.perform(post("/api/auth/refresh")
				.with(csrf())
				.cookie(new Cookie(REFRESH_TOKEN_COOKIE_NAME, oldRefreshToken))),
			HttpStatus.UNAUTHORIZED,
			"AuthException",
			AuthErrorCode.AUTH_INVALID_REFRESH_TOKEN.getMessage()
		);

		mockMvc.perform(get("/api/users/{userId}", user.getId())
				.header(HttpHeaders.AUTHORIZATION, bearer(newAccessToken)))
			.andExpect(status().isOk());
	}

	@Test
	@DisplayName("refresh token 쿠키가 없으면 토큰 재발급 요청은 401을 반환한다")
	void refresh_returnUnauthorized_whenRefreshTokenCookieIsMissing() throws Exception {
		// when & then
		assertErrorResponse(
			mockMvc.perform(post("/api/auth/refresh")
				.with(csrf())),
			HttpStatus.UNAUTHORIZED,
			"AuthException",
			AuthErrorCode.AUTH_MISSING_REFRESH_TOKEN.getMessage()
		);
	}

	@Test
	@DisplayName("비밀번호 초기화 후 발급된 임시 비밀번호로 로그인할 수 있다")
	void resetPassword_allowSignInWithTemporaryPassword() throws Exception {
		// given
		User user = createUser("임시비밀번호사용자", uniqueEmail("temporary-password"), DEFAULT_PASSWORD, UserRole.USER, false);
		given(temporaryPasswordGenerator.generate()).willReturn(TEMPORARY_PASSWORD);

		// when
		mockMvc.perform(post("/api/auth/reset-password")
				.with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
					{
						"email": "%s"
					}
					""".formatted(user.getEmail())))
			.andExpect(status().isNoContent());

		// then
		assertThat(temporaryPasswordRepository.findByUser_Id(user.getId())).isPresent();

		signIn(user.getEmail(), TEMPORARY_PASSWORD)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.userDto.id").value(user.getId().toString()));
	}

	private org.springframework.test.web.servlet.ResultActions signIn(
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

	private String accessTokenFrom(MvcResult result) throws Exception {
		JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());

		return response.get("accessToken").asText();
	}

	private String refreshTokenFrom(MvcResult result) {
		return setCookieHeaders(result)
			.stream()
			.filter(header -> header.startsWith(REFRESH_TOKEN_COOKIE_NAME + "="))
			.findFirst()
			.map(header -> header.substring(
				REFRESH_TOKEN_COOKIE_NAME.length() + 1,
				header.indexOf(';')
			))
			.orElseThrow();
	}

	private List<String> setCookieHeaders(MvcResult result) {
		return result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
	}

	private String bearer(String accessToken) {
		return "Bearer " + accessToken;
	}

	private String uniqueEmail(String prefix) {
		return prefix + "-" + UUID.randomUUID() + "@test.com";
	}
}
