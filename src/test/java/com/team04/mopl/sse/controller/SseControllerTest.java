package com.team04.mopl.sse.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.team04.mopl.auth.security.MoplUserDetails;
import com.team04.mopl.auth.security.filter.JwtAuthenticationFilter;
import com.team04.mopl.sse.service.SseService;
import com.team04.mopl.user.entity.UserRole;

@WebMvcTest(
	controllers = SseController.class,
	excludeFilters = @ComponentScan.Filter(
		type = FilterType.ASSIGNABLE_TYPE,
		classes = JwtAuthenticationFilter.class
	)
)
@AutoConfigureMockMvc(addFilters = false)
class SseControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private SseService sseService;

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	@Test
	@DisplayName("SSE 연결 요청에 성공하면 현재 사용자 기준 SSE 연결 생성")
	void connect_createSSEConnect_whenValidRequest() throws Exception {
		// given
		UUID userId = UUID.randomUUID();
		UUID lastEventId = UUID.randomUUID();

		SseEmitter sseEmitter = mock(SseEmitter.class);

		when(sseService.connect(userId, lastEventId))
			.thenReturn(sseEmitter);

		// when, then
		mockMvc.perform(get("/api/sse")
				.param("lastEventId", lastEventId.toString())
				.accept(MediaType.TEXT_EVENT_STREAM)
				.with(moplUser(userId)))
			.andExpect(status().isOk());
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