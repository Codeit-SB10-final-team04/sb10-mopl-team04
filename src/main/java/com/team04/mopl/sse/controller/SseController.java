package com.team04.mopl.sse.controller;

import java.util.UUID;

import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.team04.mopl.auth.security.MoplUserDetails;
import com.team04.mopl.sse.service.SseService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/sse")
@RequiredArgsConstructor
public class SseController implements SseControllerDocs {

	private final SseService sseService;

	@GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	@Override
	public SseEmitter connect(
		@AuthenticationPrincipal MoplUserDetails moplUserDetails,
		@RequestParam(required = false) UUID lastEventId
	) {
		UUID receiverId = moplUserDetails.getUserId();
		return sseService.connect(receiverId, lastEventId);
	}
}
