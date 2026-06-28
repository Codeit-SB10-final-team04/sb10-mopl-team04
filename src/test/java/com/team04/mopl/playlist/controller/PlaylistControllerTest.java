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
import com.team04.mopl.common.enums.SortDirection;
import com.team04.mopl.playlist.dto.request.PlaylistCreateRequest;
import com.team04.mopl.playlist.dto.request.PlaylistSearchRequest;
import com.team04.mopl.playlist.dto.request.PlaylistUpdateRequest;
import com.team04.mopl.playlist.dto.response.CursorResponsePlaylistDto;
import com.team04.mopl.playlist.dto.response.PlaylistDto;
import com.team04.mopl.playlist.dto.response.PlaylistUserSummary;
import com.team04.mopl.playlist.enums.PlaylistSortBy;
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
	@DisplayName("제목이나 설명이 비어있으면 플레이리스트 생성 요청이 400 Bad Request로 실패한다.")
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
			.andExpect(jsonPath("$.owner.userId").value(currentUserId.toString()))
			.andExpect(jsonPath("$.subscriberCount").value(0))
			.andExpect(jsonPath("$.subscribedByMe").value(false))
			.andExpect(jsonPath("$.contents").isArray());
	}

	@Test
	@DisplayName("필수 쿼리 파라미터가 누락된 요청은 지원하지 않는 메서드로 실패한다.")
	void findPlaylist_returnIsInternalServerError_whenPlaylistIdIsMissing() throws Exception {
		// given
		UUID currentUserId = UUID.randomUUID();

		// when, then
		mockMvc.perform(get("/api/playlists")
				.header("X-MOPL-USER-ID", currentUserId)
				.contentType(MediaType.APPLICATION_JSON))
			.andExpect(status().isBadRequest());
		// .andExpect(status().isInternalServerError());
	}

	@Test
	@DisplayName("플레이리스트 id가 UUID 형식이 아니면 400 Bad Request로 실패한다.")
	void findPlaylist_returnBadRequest_whenPlaylistIdIsInvalidFormat() throws Exception {
		// given
		UUID currentUserId = UUID.randomUUID();

		// when, then
		mockMvc.perform(get("/api/playlists/{playlistId}", "UUID")
				.header("X-MOPL-USER-ID", currentUserId)
				.contentType(MediaType.APPLICATION_JSON))
			.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("플레이리스트 수정 요청에 성공하면 200 OK와 플레이리스트 DTO를 반환한다.")
	void updatePlaylist_returnOK_whenValidRequest() throws Exception {
		// given
		UUID currentUserId = UUID.randomUUID();
		UUID playlistId = UUID.randomUUID();

		PlaylistUpdateRequest request = new PlaylistUpdateRequest("수정 title", "수정 description");
		PlaylistDto response = new PlaylistDto(
			playlistId,
			// TODO: UserSummary 구현 후 변경
			// new UserSummary(currentUserId, "테스트 사용자", null),
			new PlaylistUserSummary(currentUserId, "테스트 사용자", null),
			"수정 title",
			"수정 description",
			Instant.parse("2026-06-24T01:00:00Z"),
			0L,
			false,
			List.of()
		);

		when(playlistService.updatePlaylist(playlistId, request, currentUserId))
			.thenReturn(response);

		// when, then
		mockMvc.perform(patch("/api/playlists/{playlistId}", playlistId)
				.header("X-MOPL-USER-ID", currentUserId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(playlistId.toString()))
			.andExpect(jsonPath("$.owner.userId").value(currentUserId.toString()))
			.andExpect(jsonPath("$.title").value(request.title()))
			.andExpect(jsonPath("$.description").value(request.description()));
	}

	@Test
	@DisplayName("플레이리스트 수정 시 제목이 100 글자를 초과하면 400 Bad Request로 실패한다.")
	void updatePlaylist_returnBadRequest_whenTitleIsOver() throws Exception {
		// given
		UUID currentUserId = UUID.randomUUID();
		UUID playlistId = UUID.randomUUID();

		PlaylistUpdateRequest request = new PlaylistUpdateRequest("t".repeat(101), "수정 description");

		// when, then
		mockMvc.perform(patch("/api/playlists/{playlistId}", playlistId)
				.header("X-MOPL-USER-ID", currentUserId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("플레이리스트 논리 삭제 요청에 성공하면 200 OK를 반환한다.")
	void softDeletePlaylist_returnOK_whenValidRequest() throws Exception {
		// given
		UUID currentUserId = UUID.randomUUID();
		UUID playlistId = UUID.randomUUID();

		doNothing().when(playlistService).softDeletePlaylist(playlistId, currentUserId);

		// when, then
		mockMvc.perform(delete("/api/playlists/{playlistId}", playlistId)
				.header("X-MOPL-USER-ID", currentUserId))
			.andExpect(status().isOk());
	}

	@Test
	@DisplayName("플레이리스트 id가 UUID 형식이 아니라면 400 Bad Request로 실패한다.")
	void softDeletePlaylist_returnBadRequest_whenPlaylistIdIsInvalidFormat() throws Exception {
		// given
		UUID currentUserId = UUID.randomUUID();

		// when, then
		mockMvc.perform(delete("/api/playlists/{playlistId}", "UUID")
				.header("X-MOPL-USER-ID", currentUserId))
			.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("플레이리스트 목록 조회 요청에 성공하면 200 OK와 플레이리스트 커서 페이지네이션 응답 DTO를 반환한다.")
	void findAllPlaylists_returnOK_whenValidRequest() throws Exception {
		// given
		UUID currentUserId = UUID.randomUUID();
		UUID ownerId1 = UUID.randomUUID();
		UUID playlistId1 = UUID.randomUUID();
		UUID playlistId2 = UUID.randomUUID();

		PlaylistSearchRequest request = new PlaylistSearchRequest(
			"제목",
			null, null, null, null,
			2,
			SortDirection.DESCENDING,
			PlaylistSortBy.updatedAt
		);

		PlaylistDto playlistDto1 = new PlaylistDto(
			playlistId1,
			// TODO: UserSummary 구현 후 변경
			// new UserSummary(currentUserId, "테스트 사용자", null),
			new PlaylistUserSummary(currentUserId, "currentUserId 사용자", null),
			"테스트 제목1",
			"테스트 설명1",
			Instant.parse("2026-06-24T01:00:00Z"),
			0L,
			false,
			List.of()
		);

		PlaylistDto playlistDto2 = new PlaylistDto(
			playlistId2,
			// TODO: UserSummary 구현 후 변경
			// new UserSummary(currentUserId, "테스트 사용자", null),
			new PlaylistUserSummary(ownerId1, "ownerId1 사용자", null),
			"테스트 제목2",
			"테스트 설명2",
			Instant.parse("2026-06-24T00:00:00Z"),
			0L,
			false,
			List.of()
		);

		CursorResponsePlaylistDto response = new CursorResponsePlaylistDto(
			List.of(playlistDto1, playlistDto2),
			playlistDto2.updatedAt().toString(),
			playlistDto2.id(),
			true,
			3L,
			PlaylistSortBy.updatedAt.toString(),
			SortDirection.DESCENDING
		);

		when(playlistService.findAllPlaylists(request, currentUserId))
			.thenReturn(response);

		// when, then
		mockMvc.perform(get("/api/playlists")
				.param("keywordLike", "제목")
				.param("limit", "2")
				.param("sortDirection", SortDirection.DESCENDING.toString())
				.param("sortBy", PlaylistSortBy.updatedAt.toString())
				.header("X-MOPL-USER-ID", currentUserId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.size()").value(2))
			.andExpect(jsonPath("$.data[0].id").value(playlistId1.toString()))
			.andExpect(jsonPath("$.data[1].id").value(playlistId2.toString()))
			.andExpect(jsonPath("$.nextCursor").value(playlistDto2.updatedAt().toString()))
			.andExpect(jsonPath("$.nextIdAfter").value(playlistDto2.id().toString()))
			.andExpect(jsonPath("$.hasNext").value(true))
			.andExpect(jsonPath("$.totalCount").value(3))
			.andExpect(jsonPath("$.sortBy").value(PlaylistSortBy.updatedAt.toString()))
			.andExpect(jsonPath("$.sortDirection").value(SortDirection.DESCENDING.toString()));
	}

	@Test
	@DisplayName("플레이리스트 목록 조회 요청에서 필수 파라미터 limit이 없으면 400 Bad Request로 실패한다.")
	void findAllPlaylists_returnBadRequest_whenLimitIsMissing() throws Exception {
		// given
		UUID currentUserId = UUID.randomUUID();

		// when, then
		mockMvc.perform(get("/api/playlists")
				.param("keywordLike", "제목")
				.param("sortDirection", SortDirection.DESCENDING.toString())
				.param("sortBy", PlaylistSortBy.updatedAt.toString())
				.header("X-MOPL-USER-ID", currentUserId))
			.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("플레이리스트 목록 조회 요청에서 필수 파라미터 sortBy나 sortDirection이 없으면 400 Bad Request로 실패한다.")
	void findAllPlaylists_returnBadRequest_whenSortByOrSortDirectionIsMissing() throws Exception {
		// given
		UUID currentUserId = UUID.randomUUID();
		
		// when, then
		mockMvc.perform(get("/api/playlists")
				.param("keywordLike", "제목")
				.param("limit", "2")
				.param("sortBy", PlaylistSortBy.updatedAt.toString())
				.header("X-MOPL-USER-ID", currentUserId))
			.andExpect(status().isBadRequest());
	}
}
