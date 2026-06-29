package com.team04.mopl.content.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team04.mopl.auth.security.filter.JwtAuthenticationFilter;
import com.team04.mopl.common.dto.CursorPageResponse;
import com.team04.mopl.content.dto.request.ContentCreateRequest;
import com.team04.mopl.content.dto.response.ContentDto;
import com.team04.mopl.content.entity.ContentType;
import com.team04.mopl.content.exception.ContentErrorCode;
import com.team04.mopl.content.exception.ContentException;
import com.team04.mopl.content.service.ContentService;

@WebMvcTest(
	controllers = ContentController.class,
	excludeFilters = @ComponentScan.Filter( // 컨트롤러 테스트에서 JWT 인증 필터 제외
		type = FilterType.ASSIGNABLE_TYPE,
		classes = JwtAuthenticationFilter.class
	)
)
@AutoConfigureMockMvc(addFilters = false)
class ContentControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private ContentService contentService;

	// ========== getContent ==========

	@Test
	@DisplayName("콘텐츠 단건 조회 요청에 성공하면 200 OK와 콘텐츠 Dto를 반환한다.")
	void getContent_returnOK_whenValidRequest() throws Exception {
		// given
		UUID contentId = UUID.randomUUID();
		ContentDto response = new ContentDto(
			contentId,
			ContentType.movie,
			"인터스텔라",
			"우주를 여행하는 이야기",
			"https://image.tmdb.org/t/p/w500/abc.jpg",
			List.of("영화"),
			new BigDecimal("4.50"),
			100L,
			5000L
		);

		when(contentService.getContent(contentId)).thenReturn(response);

		// when & then
		mockMvc.perform(get("/api/contents/{contentId}", contentId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(contentId.toString()))
			.andExpect(jsonPath("$.title").value("인터스텔라"))
			.andExpect(jsonPath("$.tags[0]").value("영화"));
	}

	@Test
	@DisplayName("UUID 형식이 아니면 400 Bad Request로 실패한다.")
	void getContent_returnBadRequest_whenInvalidUuidFormat() throws Exception {
		// given: UUID 형식이 아닌 문자열
		String invalidId = "not-a-uuid";

		// when & then: @PathVariable UUID 바인딩 실패 → 400
		mockMvc.perform(get("/api/contents/{contentId}", invalidId))
			.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("존재하지 않는 콘텐츠를 조회하면 404 Not Found를 반환한다.")
	void getContent_returnNotFound_whenContentNotExists() throws Exception {
		UUID contentId = UUID.randomUUID();
		when(contentService.getContent(contentId))
			.thenThrow(new ContentException(ContentErrorCode.CONTENT_NOT_FOUND));

		mockMvc.perform(get("/api/contents/{contentId}", contentId))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.exceptionName").value("ContentException"))
			.andExpect(jsonPath("$.message").value("콘텐츠를 찾을 수 없습니다."));
	}

	// ========== createContent ==========

	@Test
	@DisplayName("콘텐츠 생성 요청에 성공하면 201 Created와 콘텐츠 Dto를 반환한다.")
	void createContent_returnCreated_whenValidRequest() throws Exception {
		// given
		UUID contentId = UUID.randomUUID();
		ContentCreateRequest request = new ContentCreateRequest(
			"movie", "인터스텔라", "우주를 여행하는 이야기", List.of("액션", "SF")
		);
		ContentDto response = new ContentDto(
			contentId,
			ContentType.movie,
			"인터스텔라",
			"우주를 여행하는 이야기",
			"http://localhost:8080/thumbnails/abc.png",
			List.of("액션", "SF"),
			BigDecimal.ZERO,
			0L,
			0L
		);

		MockMultipartFile thumbnail = new MockMultipartFile(
			"thumbnail", "thumb.png", MediaType.IMAGE_PNG_VALUE, "image-data".getBytes()
		);
		MockMultipartFile requestPart = new MockMultipartFile(
			"contentCreateRequest", "", MediaType.APPLICATION_JSON_VALUE,
			objectMapper.writeValueAsBytes(request)
		);

		when(contentService.createContent(any(), any())).thenReturn(response);

		// when & then
		mockMvc.perform(multipart("/api/contents")
				.file(thumbnail)
				.file(requestPart))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").value(contentId.toString()))
			.andExpect(jsonPath("$.title").value("인터스텔라"))
			.andExpect(jsonPath("$.tags[0]").value("액션"));
	}

	// ========== getContents ==========

	@Test
	@DisplayName("콘텐츠 목록 조회 요청에 성공하면 200 OK와 페이지 응답을 반환한다.")
	void getContents_returnOK_whenValidRequest() throws Exception {
		// given
		UUID contentId = UUID.randomUUID();
		ContentDto contentDto = new ContentDto(
			contentId, ContentType.movie, "인터스텔라", "우주 이야기",
			"https://image.tmdb.org/t/p/w500/abc.jpg",
			List.of("SF"), new BigDecimal("4.50"), 100L, 5000L
		);
		CursorPageResponse<ContentDto> response = new CursorPageResponse<>(
			List.of(contentDto), null, null, false, 1L, "watcherCount", "DESC"
		);

		when(contentService.getContents(any())).thenReturn(response);

		// when & then
		mockMvc.perform(get("/api/contents")
				.param("limit", "10")
				.param("sortBy", "watcherCount")
				.param("sortDirection", "DESC"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data[0].id").value(contentId.toString()))
			.andExpect(jsonPath("$.data[0].title").value("인터스텔라"))
			.andExpect(jsonPath("$.hasNext").value(false))
			.andExpect(jsonPath("$.totalCount").value(1));
	}

	@Test
	@DisplayName("콘텐츠 목록 조회 시 hasNext=true이면 nextCursor와 nextIdAfter가 포함된다.")
	void getContents_returnNextCursor_whenHasNextPage() throws Exception {
		// given
		UUID nextId = UUID.randomUUID();
		CursorPageResponse<ContentDto> response = new CursorPageResponse<>(
			List.of(), "100", nextId.toString(), true, 10L, "watcherCount", "DESC"
		);

		when(contentService.getContents(any())).thenReturn(response);

		// when & then
		mockMvc.perform(get("/api/contents").param("limit", "5"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.hasNext").value(true))
			.andExpect(jsonPath("$.nextCursor").value("100"))
			.andExpect(jsonPath("$.nextIdAfter").value(nextId.toString()));
	}

	@Test
	@DisplayName("필수 필드가 누락되면 400 Bad Request를 반환한다.")
	void createContent_returnBadRequest_whenRequiredFieldMissing() throws Exception {
		// given: title 누락
		ContentCreateRequest request = new ContentCreateRequest(
			"movie", "", "우주를 여행하는 이야기", null
		);

		MockMultipartFile thumbnail = new MockMultipartFile(
			"thumbnail", "thumb.png", MediaType.IMAGE_PNG_VALUE, "image-data".getBytes()
		);
		MockMultipartFile requestPart = new MockMultipartFile(
			"contentCreateRequest", "", MediaType.APPLICATION_JSON_VALUE,
			objectMapper.writeValueAsBytes(request)
		);

		// when & then
		mockMvc.perform(multipart("/api/contents")
				.file(thumbnail)
				.file(requestPart))
			.andExpect(status().isBadRequest());
	}
}
