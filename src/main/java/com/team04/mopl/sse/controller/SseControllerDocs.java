package com.team04.mopl.sse.controller;

import java.util.UUID;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.team04.mopl.auth.security.MoplUserDetails;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "SSE")
public interface SseControllerDocs {

	@Operation(summary = "SSE 연결", description = "lastEventId 이후 발생한 미수신 알림을 복구하여 SSE로 전송합니다.")
	SseEmitter connect(
		MoplUserDetails moplUserDetails,
		UUID lastEventId
	);
}
