package com.team04.mopl.support;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

// MockMvc 통합 테스트에서 공통 ErrorResponse 응답 계약을 검증하는 테스트 유틸리티
public final class ErrorResponseAssertions {

	private ErrorResponseAssertions() {
	}

	public static ResultActions assertErrorResponse(
		ResultActions resultActions,
		HttpStatus expectedStatus,
		String expectedExceptionName,
		String expectedMessage
	) throws Exception {
		return resultActions
			.andExpect(status().is(expectedStatus.value()))
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.exceptionName").value(expectedExceptionName))
			.andExpect(jsonPath("$.message").value(expectedMessage))
			.andExpect(jsonPath("$.details").isMap());
	}
}
