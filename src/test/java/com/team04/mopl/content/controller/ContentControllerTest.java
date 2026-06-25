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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team04.mopl.content.dto.response.ContentDto;
import com.team04.mopl.content.entity.ContentType;
import com.team04.mopl.content.service.ContentService;

@WebMvcTest(ContentController.class)
@AutoConfigureMockMvc(addFilters = false)
class ContentControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private ContentService contentService;

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
}