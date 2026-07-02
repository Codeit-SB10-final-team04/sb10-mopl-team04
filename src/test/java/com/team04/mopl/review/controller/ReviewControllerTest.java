package com.team04.mopl.review.controller;

import static org.mockito.ArgumentMatchers.*;
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
import com.team04.mopl.review.dto.request.ReviewUpdateRequest;
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

	// ========== updateReview ==========

	@Test
	@DisplayName("리뷰 수정 요청에 성공하면 200 OK와 ReviewDto를 반환한다")
	void updateReview_returnOk_whenValidRequest() throws Exception {
		// given
		UUID reviewId = UUID.randomUUID();
		UUID contentId = UUID.randomUUID();
		ReviewUpdateRequest request = new ReviewUpdateRequest("수정된 리뷰", (short)4);

		ReviewDto response = new ReviewDto(
			reviewId,
			contentId,
			new UserSummary(UUID.randomUUID(), "테스트유저", null),
			"수정된 리뷰",
			(short)4
		);

		when(reviewService.updateReview(eq(reviewId), any(), any())).thenReturn(response);

		// when & then
		mockMvc.perform(patch("/api/reviews/{reviewId}", reviewId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.text").value("수정된 리뷰"))
			.andExpect(jsonPath("$.rating").value(4));
	}

	@Test
	@DisplayName("존재하지 않는 리뷰이면 404 Not Found를 반환한다")
	void updateReview_returnNotFound_whenReviewNotFound() throws Exception {
		// given
		UUID reviewId = UUID.randomUUID();
		ReviewUpdateRequest request = new ReviewUpdateRequest("수정된 리뷰", (short)4);

		when(reviewService.updateReview(eq(reviewId), any(), any()))
			.thenThrow(new ReviewException(ReviewErrorCode.REVIEW_NOT_FOUND));

		// when & then
		mockMvc.perform(patch("/api/reviews/{reviewId}", reviewId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value(ReviewErrorCode.REVIEW_NOT_FOUND.getMessage()));
	}

	@Test
	@DisplayName("리뷰 작성자가 아니면 403 Forbidden을 반환한다")
	void updateReview_returnForbidden_whenNotOwner() throws Exception {
		// given
		UUID reviewId = UUID.randomUUID();
		ReviewUpdateRequest request = new ReviewUpdateRequest("수정된 리뷰", (short)4);

		when(reviewService.updateReview(eq(reviewId), any(), any()))
			.thenThrow(new ReviewException(ReviewErrorCode.REVIEW_FORBIDDEN));

		// when & then
		mockMvc.perform(patch("/api/reviews/{reviewId}", reviewId)
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.message").value(ReviewErrorCode.REVIEW_FORBIDDEN.getMessage()));
	}

	// ========== deleteReview ==========

	@Test
	@DisplayName("리뷰 삭제 요청에 성공하면 204 No Content를 반환한다")
	void deleteReview_returnNoContent_whenValidRequest() throws Exception {
		// given
		UUID reviewId = UUID.randomUUID();

		doNothing().when(reviewService).deleteReview(eq(reviewId), any());

		// when & then
		mockMvc.perform(delete("/api/reviews/{reviewId}", reviewId))
			.andExpect(status().isNoContent());
	}

	@Test
	@DisplayName("존재하지 않는 리뷰 삭제 시 404 Not Found를 반환한다")
	void deleteReview_returnNotFound_whenReviewNotFound() throws Exception {
		// given
		UUID reviewId = UUID.randomUUID();

		doThrow(new ReviewException(ReviewErrorCode.REVIEW_NOT_FOUND))
			.when(reviewService).deleteReview(eq(reviewId), any());

		// when & then
		mockMvc.perform(delete("/api/reviews/{reviewId}", reviewId))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.message").value(ReviewErrorCode.REVIEW_NOT_FOUND.getMessage()));
	}

	@Test
	@DisplayName("리뷰 작성자가 아니면 삭제 시 403 Forbidden을 반환한다")
	void deleteReview_returnForbidden_whenNotOwner() throws Exception {
		// given
		UUID reviewId = UUID.randomUUID();

		doThrow(new ReviewException(ReviewErrorCode.REVIEW_FORBIDDEN))
			.when(reviewService).deleteReview(eq(reviewId), any());

		// when & then
		mockMvc.perform(delete("/api/reviews/{reviewId}", reviewId))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.message").value(ReviewErrorCode.REVIEW_FORBIDDEN.getMessage()));
	}
}
