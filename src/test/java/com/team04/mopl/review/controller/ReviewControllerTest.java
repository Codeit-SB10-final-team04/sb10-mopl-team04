package com.team04.mopl.review.controller;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team04.mopl.auth.security.filter.JwtAuthenticationFilter;
import com.team04.mopl.common.dto.UserSummary;
import com.team04.mopl.review.dto.request.ReviewCreateRequest;
import com.team04.mopl.review.dto.response.ReviewDto;
import com.team04.mopl.review.exception.ReviewErrorCode;
import com.team04.mopl.review.exception.ReviewException;
import com.team04.mopl.review.service.ReviewService;

@WebMvcTest(
	controllers = ReviewController.class,
	excludeFilters = @ComponentScan.Filter(
		type = FilterType.ASSIGNABLE_TYPE,
		classes = JwtAuthenticationFilter.class
	)
)
@AutoConfigureMockMvc(addFilters = false)
class ReviewControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@MockitoBean
	private ReviewService reviewService;

	// ========== createReview ==========

	@Test
	@DisplayName("리뷰 등록 요청에 성공하면 200 OK와 ReviewDto를 반환한다.")
	void createReview_returnOk_whenValidRequest() throws Exception {
		// given
		UUID contentId = UUID.randomUUID();
		ReviewCreateRequest request = new ReviewCreateRequest(contentId, "재밌어요", (short)5);

		ReviewDto response = new ReviewDto(
			UUID.randomUUID(),
			contentId,
			new UserSummary(UUID.randomUUID(), "테스트유저", null),
			"재밌어요",
			(short)5
		);

		when(reviewService.createReview(any(), any())).thenReturn(response);

		// when & then
		mockMvc.perform(post("/api/reviews")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.contentId").value(contentId.toString()))
			.andExpect(jsonPath("$.text").value("재밌어요"))
			.andExpect(jsonPath("$.rating").value(5));
	}

	@Test
	@DisplayName("contentId가 null이면 400 Bad Request를 반환한다.")
	void createReview_returnBadRequest_whenContentIdIsNull() throws Exception {
		// given
		ReviewCreateRequest request = new ReviewCreateRequest(null, "재밌어요", (short)5);

		// when & then
		mockMvc.perform(post("/api/reviews")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("text가 blank이면 400 Bad Request를 반환한다.")
	void createReview_returnBadRequest_whenTextIsBlank() throws Exception {
		// given
		ReviewCreateRequest request = new ReviewCreateRequest(UUID.randomUUID(), "  ", (short)5);

		// when & then
		mockMvc.perform(post("/api/reviews")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("rating이 범위를 벗어나면 400 Bad Request를 반환한다.")
	void createReview_returnBadRequest_whenRatingOutOfRange() throws Exception {
		// given
		ReviewCreateRequest request = new ReviewCreateRequest(UUID.randomUUID(), "재밌어요", (short)6);

		// when & then
		mockMvc.perform(post("/api/reviews")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("이미 리뷰를 작성한 경우 409 Conflict를 반환한다.")
	void createReview_returnConflict_whenReviewAlreadyExists() throws Exception {
		// given
		ReviewCreateRequest request = new ReviewCreateRequest(UUID.randomUUID(), "재밌어요", (short)5);

		when(reviewService.createReview(any(), any()))
			.thenThrow(new ReviewException(ReviewErrorCode.REVIEW_ALREADY_EXISTS));

		// when & then
		mockMvc.perform(post("/api/reviews")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.message").value(ReviewErrorCode.REVIEW_ALREADY_EXISTS.getMessage()));
	}
}
