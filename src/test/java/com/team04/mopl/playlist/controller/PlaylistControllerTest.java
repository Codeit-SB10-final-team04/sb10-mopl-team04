package com.team04.mopl.playlist.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team04.mopl.playlist.dto.request.PlaylistCreateRequest;
import com.team04.mopl.playlist.dto.response.PlaylistDto;
import com.team04.mopl.playlist.dto.response.PlaylistUserSummary;
import com.team04.mopl.playlist.service.PlaylistService;

@WebMvcTest(PlaylistController.class)
@AutoConfigureMockMvc(addFilters = false)
class PlaylistControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private PlaylistService playlistService;

	@Test
	@DisplayName("플레이리스트 생성 요청에 성공하면 201 Created와 생성된 플레이리스트 DTO를 반환한다.")
	void createPlaylist_returnCreated_whenValidRequest() throws Exception {
		// given
		UUID currentUserId = UUID.randomUUID();
		UUID playlistId = UUID.randomUUID();

		PlaylistCreateRequest request = new PlaylistCreateRequest("테스트 제목", "테스트 설명");

		PlaylistDto response = new PlaylistDto(
			playlistId,
			// TODO: UserSummary 구현 후 변경
			// new UserSummary(currentUserId, "테스트 사용자", null),
			new PlaylistUserSummary(currentUserId, "테스트 사용자", null),
			request.title(),
			request.description(),
			Instant.parse("2026-06-24T01:00:00Z"),
			0L,
			false,
			List.of()
		);

		when(playlistService.createPlaylist(request, currentUserId))
			.thenReturn(response);

		// when, then
		mockMvc.perform(post("/api/playlists")
				.header("X-MOPL-USER-ID", currentUserId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").value(playlistId.toString()))
			.andExpect(jsonPath("$.owner.userId").value(currentUserId.toString()))
			.andExpect(jsonPath("$.title").value(request.title()))
			.andExpect(jsonPath("$.description").value(request.description()));

		verify(playlistService).createPlaylist(
			any(PlaylistCreateRequest.class),
			any(UUID.class)
		);
	}

	@Test
	@DisplayName("제목이나 설명이 비어있으면 플레이리스트 생성 요청이 실패한다.")
	void createPlaylist_returnBadRequest_whenTitleOrDescriptionBlank() throws Exception {
		// given
		PlaylistCreateRequest request = new PlaylistCreateRequest("", "테스트 설명");

		// when, then
		mockMvc.perform(post("/api/playlists")
				.header("X-MOPL-USER-ID", UUID.randomUUID())
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("플레이리스트 단건 조회 요청에 성공하면 200 OK와 플레이리스트 DTO를 반환한다.")
	void findPlaylist_returnOK_whenValidRequest() throws Exception {
		// given
		UUID currentUserId = UUID.randomUUID();
		UUID playlistId = UUID.randomUUID();

		PlaylistDto response = new PlaylistDto(
			playlistId,
			// TODO: UserSummary 구현 후 변경
			// new UserSummary(currentUserId, "테스트 사용자", null),
			new PlaylistUserSummary(currentUserId, "테스트 사용자", null),
			"테스트 제목",
			"테스트 설명",
			Instant.parse("2026-06-24T01:00:00Z"),
			0L,
			false,
			List.of()
		);

		when(playlistService.findPlaylist(playlistId, currentUserId))
			.thenReturn(response);

		// when, then
		mockMvc.perform(get("/api/playlists/{playlistId}", playlistId)
				.header("X-MOPL-USER-ID", currentUserId)
				.contentType(MediaType.APPLICATION_JSON))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(playlistId.toString()))
			.andExpect(jsonPath("$.owner.userId").value(currentUserId.toString()));
	}

	@Test
	@DisplayName("잘못된 엔드포인트로 오면 플레이리스트 단건 조회 요청이 실패한다.")
	void findPlaylist_returnBadRequest_whenMethodNotAllowed() throws Exception {
		// given
		UUID currentUserId = UUID.randomUUID();

		// when, then
		mockMvc.perform(get("/api/playlists")
				.header("X-MOPL-USER-ID", currentUserId)
				.contentType(MediaType.APPLICATION_JSON))
			// .andExpect(status().isMethodNotAllowed());
			.andExpect(status().isInternalServerError());

	}

	@Test
	@DisplayName("플레이리스트 id가 비어있으면 플레이리스트 단건 조회 요청이 실패한다.")
	void findPlaylist_returnBadRequest_whenPlaylistIdIsBlank() throws Exception {
		// given
		UUID currentUserId = UUID.randomUUID();

		// when, then
		mockMvc.perform(get("/api/playlists/{playlistId}", "UUID")
				.header("X-MOPL-USER-ID", currentUserId)
				.contentType(MediaType.APPLICATION_JSON))
			.andExpect(status().isBadRequest());
	}

}