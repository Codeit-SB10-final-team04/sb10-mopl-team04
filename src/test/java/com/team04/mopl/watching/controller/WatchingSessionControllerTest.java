package com.team04.mopl.watching.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.team04.mopl.auth.security.filter.JwtAuthenticationFilter;
import com.team04.mopl.common.dto.ContentSummary;
import com.team04.mopl.common.dto.CursorResponse;
import com.team04.mopl.common.dto.UserSummary;
import com.team04.mopl.content.entity.ContentType;
import com.team04.mopl.watching.dto.response.WatchingSessionDto;
import com.team04.mopl.watching.dto.request.WatchingSessionPageRequest;
import com.team04.mopl.watching.service.WatchingSessionService;

@WebMvcTest(
	controllers = WatchingSessionController.class,
	excludeFilters = @ComponentScan.Filter(
		type = FilterType.ASSIGNABLE_TYPE,
		classes = JwtAuthenticationFilter.class
	)
)
@AutoConfigureMockMvc(addFilters = false)
class WatchingSessionControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private WatchingSessionService watchingSessionService;

	@Test
	@DisplayName("콘텐츠별 시청 세션 목록 조회에 성공하면 200과 CursorResponse를 반환한다")
	void findByContent_returnOk_whenValidRequest() throws Exception {
		// given
		UUID contentId = UUID.randomUUID();
		WatchingSessionDto sessionDto = createSessionDto(contentId);

		CursorResponse<WatchingSessionDto> response = new CursorResponse<>(
			List.of(sessionDto), null, null, false, 1L, "createdAt", "ASCENDING"
		);

		given(watchingSessionService.findByContentId(eq(contentId), any(WatchingSessionPageRequest.class)))
			.willReturn(response);

		// when & then
		mockMvc.perform(get("/api/contents/{contentId}/watching-sessions", contentId)
				.param("limit", "10")
				.param("sortDirection", "ASCENDING")
				.param("sortBy", "createdAt"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data[0].id").value(sessionDto.id().toString()))
			.andExpect(jsonPath("$.totalCount").value(1))
			.andExpect(jsonPath("$.hasNext").value(false));
	}

	@Test
	@DisplayName("limit 없이 목록 조회하면 400을 반환한다")
	void findByContent_returnBadRequest_whenLimitMissing() throws Exception {
		mockMvc.perform(get("/api/contents/{contentId}/watching-sessions", UUID.randomUUID())
				.param("sortDirection", "ASCENDING")
				.param("sortBy", "createdAt"))
			.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("cursor만 있고 idAfter가 없으면 400을 반환한다")
	void findByContent_returnBadRequest_whenCursorWithoutIdAfter() throws Exception {
		mockMvc.perform(get("/api/contents/{contentId}/watching-sessions", UUID.randomUUID())
				.param("cursor", Instant.now().toString())
				.param("limit", "10")
				.param("sortDirection", "ASCENDING")
				.param("sortBy", "createdAt"))
			.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("유저별 시청 세션 조회 시 시청 중이면 200과 DTO를 반환한다")
	void findByWatcher_returnOkWithBody_whenWatching() throws Exception {
		// given
		UUID watcherId = UUID.randomUUID();
		WatchingSessionDto sessionDto = createSessionDto(UUID.randomUUID());

		given(watchingSessionService.findByWatcherId(watcherId)).willReturn(Optional.of(sessionDto));

		// when & then
		mockMvc.perform(get("/api/users/{watcherId}/watching-sessions", watcherId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(sessionDto.id().toString()));
	}

	@Test
	@DisplayName("유저별 시청 세션 조회 시 시청 중이 아니면 200과 empty body를 반환한다")
	void findByWatcher_returnOkWithEmptyBody_whenNotWatching() throws Exception {
		// given
		UUID watcherId = UUID.randomUUID();

		given(watchingSessionService.findByWatcherId(watcherId)).willReturn(Optional.empty());

		// when & then
		mockMvc.perform(get("/api/users/{watcherId}/watching-sessions", watcherId))
			.andExpect(status().isOk())
			.andExpect(content().string(""));
	}

	private WatchingSessionDto createSessionDto(UUID contentId) {
		return new WatchingSessionDto(
			UUID.randomUUID(),
			Instant.now(),
			new UserSummary(UUID.randomUUID(), "테스트유저", null),
			new ContentSummary(
				contentId, ContentType.movie, "테스트콘텐츠", "설명", null,
				List.of(), BigDecimal.ZERO, 0L
			)
		);
	}
}
