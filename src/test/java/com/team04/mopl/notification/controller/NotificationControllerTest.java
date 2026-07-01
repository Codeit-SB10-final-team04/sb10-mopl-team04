package com.team04.mopl.notification.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.List;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team04.mopl.auth.security.MoplUserDetails;
import com.team04.mopl.auth.security.filter.JwtAuthenticationFilter;
import com.team04.mopl.common.enums.SortDirection;
import com.team04.mopl.notification.dto.request.NotificationPageRequest;
import com.team04.mopl.notification.dto.response.CursorResponseNotificationDto;
import com.team04.mopl.notification.dto.response.NotificationDto;
import com.team04.mopl.notification.enums.NotificationLevel;
import com.team04.mopl.notification.enums.NotificationSortBy;
import com.team04.mopl.notification.service.NotificationService;
import com.team04.mopl.user.entity.UserRole;

@WebMvcTest(
	controllers = NotificationController.class,
	excludeFilters = @ComponentScan.Filter( // 컨트롤러 테스트에서 JWT 인증 필터 제외
		type = FilterType.ASSIGNABLE_TYPE,
		classes = JwtAuthenticationFilter.class
	)
)
@AutoConfigureMockMvc(addFilters = false)
class NotificationControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private NotificationService notificationService;

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	@Test
	@DisplayName("알림 목록 조회 요청에 성공하면 200 OK와 알림 커서 페이지네이션 응답 DTO를 반환한다.")
	void findAllNotifications_returnOK_whenValidRequest() throws Exception {
		// given
		UUID currentUserId = UUID.randomUUID();
		UUID notificationId1 = UUID.randomUUID();
		UUID notificationId2 = UUID.randomUUID();

		NotificationPageRequest request = new NotificationPageRequest(
			null, null,
			2,
			SortDirection.DESCENDING,
			NotificationSortBy.createdAt
		);

		NotificationDto notificationDto1 = new NotificationDto(
			notificationId1,
			Instant.parse("2026-06-24T01:00:00Z"),
			currentUserId,
			"테스트 제목1",
			"테스트 내용1",
			NotificationLevel.INFO
		);
		NotificationDto notificationDto2 = new NotificationDto(
			notificationId2,
			Instant.parse("2026-06-24T00:00:00Z"),
			currentUserId,
			"테스트 제목2",
			"테스트 내용2",
			NotificationLevel.INFO
		);

		CursorResponseNotificationDto response = new CursorResponseNotificationDto(
			List.of(notificationDto1, notificationDto2),
			notificationDto2.createdAt().toString(),
			notificationId2,
			true,
			3L,
			NotificationSortBy.createdAt.toString(),
			SortDirection.DESCENDING
		);

		when(notificationService.findAllNotifications(request, currentUserId))
			.thenReturn(response);

		// when, then
		mockMvc.perform(get("/api/notifications")
				.param("limit", "2")
				.param("sortDirection", SortDirection.DESCENDING.toString())
				.param("sortBy", NotificationSortBy.createdAt.toString())
				.with(moplUser(currentUserId))
				.contentType(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.size()").value(2))
			.andExpect(jsonPath("$.data[0].id").value(notificationId1.toString()))
			.andExpect(jsonPath("$.data[0].receiverId").value(currentUserId.toString()))
			.andExpect(jsonPath("$.data[1].id").value(notificationId2.toString()))
			.andExpect(jsonPath("$.data[1].receiverId").value(currentUserId.toString()))
			.andExpect(jsonPath("$.nextCursor").value(notificationDto2.createdAt().toString()))
			.andExpect(jsonPath("$.nextIdAfter").value(notificationDto2.id().toString()))
			.andExpect(jsonPath("$.hasNext").value(true))
			.andExpect(jsonPath("$.totalCount").value(3))
			.andExpect(jsonPath("$.sortBy").value(NotificationSortBy.createdAt.toString()))
			.andExpect(jsonPath("$.sortDirection").value(SortDirection.DESCENDING.toString()));
	}

	@Test
	@DisplayName("알림 목록 조회 요청에서 필수 파라미터 limit이 없으면 400 Bad Request로 실패한다.")
	void findAllNotifications_returnBadRequest_whenLimitIsMissing() throws Exception {
		// given
		UUID currentUserId = UUID.randomUUID();

		// when, then
		mockMvc.perform(get("/api/notifications")
				.param("sortDirection", SortDirection.DESCENDING.toString())
				.param("sortBy", NotificationSortBy.createdAt.toString())
				.with(moplUser(currentUserId))
				.contentType(MediaType.APPLICATION_JSON))
			.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("알림 목록 조회 요청에서 필수 파라미터 sortBy나 sortDirection이 없으면 400 Bad Request로 실패한다.")
	void findAllNotifications_returnBadRequest_whenSortByOrSortDirectionIsMissing() throws Exception {
		// given
		UUID currentUserId = UUID.randomUUID();

		// when, then
		mockMvc.perform(get("/api/notifications")
				.param("limit", "2")
				.param("sortBy", NotificationSortBy.createdAt.toString())
				.with(moplUser(currentUserId))
				.contentType(MediaType.APPLICATION_JSON))
			.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("알림 목록 조회 요청에서 cursor만 있고 idAfter가 없으면 400 Bad Request로 실패한다.")
	void findAllNotifications_returnBadRequest_whenOnlyCursorProvided() throws Exception {
		// given
		UUID currentUserId = UUID.randomUUID();

		// when, then
		mockMvc.perform(get("/api/notifications")
				.param("cursor", "2026-06-24T01:00:00Z")
				.param("limit", "2")
				.param("sortDirection", SortDirection.DESCENDING.toString())
				.param("sortBy", NotificationSortBy.createdAt.toString())
				.with(moplUser(currentUserId))
				.contentType(MediaType.APPLICATION_JSON))
			.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("알림 읽음 요청에 성공하면 204 No Content가 발생한다.")
	void readNotification_returnNoContent_whenValidRequest() throws Exception {
		// given
		UUID notificationId = UUID.randomUUID();
		UUID currentUserId = UUID.randomUUID();

		doNothing()
			.when(notificationService)
			.readNotification(notificationId, currentUserId);

		// when, then
		mockMvc.perform(delete("/api/notifications/{notificationId}", notificationId)
				.with(moplUser(currentUserId)))
			.andExpect(status().isNoContent());
	}

	@Test
	@DisplayName("notificationId가 잘못된 UUID 형식으로 들어오면 400 Bad Request가 발생한다.")
	void readNotification_returnBadRequest_whenInvalidNotificationIdFormat() throws Exception {
		// given
		UUID currentUserId = UUID.randomUUID();

		// when, then
		mockMvc.perform(delete("/api/notifications/{notificationId}", "Invalid-UUID")
				.with(moplUser(currentUserId)))
			.andExpect(status().isBadRequest());
	}

	private RequestPostProcessor moplUser(UUID userId) {
		return request -> {
			MoplUserDetails moplUserDetails = MoplUserDetails.authenticated(
				userId,
				"test@example.com",
				UserRole.USER
			);

			UsernamePasswordAuthenticationToken authentication =
				new UsernamePasswordAuthenticationToken(
					moplUserDetails,
					null,
					moplUserDetails.getAuthorities()
				);

			SecurityContextHolder.getContext().setAuthentication(authentication);
			return request;
		};
	}
}