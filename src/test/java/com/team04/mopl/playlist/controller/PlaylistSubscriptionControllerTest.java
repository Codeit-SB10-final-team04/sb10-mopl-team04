package com.team04.mopl.playlist.controller;

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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.team04.mopl.auth.security.MoplUserDetails;
import com.team04.mopl.auth.security.filter.JwtAuthenticationFilter;
import com.team04.mopl.playlist.service.PlaylistSubscriptionService;
import com.team04.mopl.user.entity.UserRole;

@WebMvcTest(
	controllers = PlaylistSubscriptionController.class,
	excludeFilters = @ComponentScan.Filter(
		type = FilterType.ASSIGNABLE_TYPE,
		classes = JwtAuthenticationFilter.class
	)
)
@AutoConfigureMockMvc(addFilters = false)
class PlaylistSubscriptionControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private PlaylistSubscriptionService playlistSubscriptionService;

	@AfterEach
	void tearDown() {
		SecurityContextHolder.clearContext();
	}

	@Test
	@DisplayName("플레이리스트 구독 요청에 성공하면 204 No Content를 반환한다.")
	void subscribePlaylist_returnNoContent_whenValidRequest() throws Exception {
		// given
		UUID playlistId = UUID.randomUUID();
		UUID currentUserId = UUID.randomUUID();

		doNothing()
			.when(playlistSubscriptionService)
			.subscribePlaylist(playlistId, currentUserId);

		// when, then
		mockMvc.perform(post("/api/playlists/{playlistId}/subscription", playlistId)
				.with(moplUser(currentUserId)))
			.andExpect(status().isNoContent());
	}

	@Test
	@DisplayName("플레이리스트 id가 UUID 형식이 아니면 400 Bad Request를 반환한다.")
	void subscribePlaylist_returnBadRequest_whenPlaylistIdIsInvalidFormat() throws Exception {
		// given
		UUID currentUserId = UUID.randomUUID();

		// when, then
		mockMvc.perform(post("/api/playlists/{playlistId}/subscription", "UUID")
				.with(moplUser(currentUserId)))
			.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("플레이리스트 구독 취소 요청에 성공하면 204 No Content를 반환한다.")
	void unSubscribePlaylist_returnNoContent_whenValidRequest() throws Exception {
		// given
		UUID playlistId = UUID.randomUUID();
		UUID currentUserId = UUID.randomUUID();

		doNothing()
			.when(playlistSubscriptionService)
			.unSubscribePlaylist(playlistId, currentUserId);

		// when, then
		mockMvc.perform(delete("/api/playlists/{playlistId}/subscription", playlistId)
				.with(moplUser(currentUserId)))
			.andExpect(status().isNoContent());
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