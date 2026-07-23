package com.team04.mopl.sse.controller;

import java.util.UUID;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.team04.mopl.auth.security.MoplUserDetails;
import com.team04.mopl.common.exception.ErrorResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "SSE")
public interface SseControllerDocs {

	@Operation(summary = "SSE 연결", description = "lastEventId 이후 발생한 미수신 알림을 복구하여 SSE로 전송합니다.")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "성공"),
		@ApiResponse(responseCode = "401", description = "인증 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
		@ApiResponse(responseCode = "500", description = "서버 오류", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
	})
	SseEmitter connect(
		@Parameter(hidden = true) MoplUserDetails moplUserDetails,
		@Parameter(description = "마지막으로 수신한 이벤트 ID") UUID lastEventId
	);
}
