package com.team04.mopl.sse.controller;

import java.util.UUID;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.team04.mopl.auth.security.MoplUserDetails;

import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "sse-controller")
public interface SseControllerDocs {

	SseEmitter connect(
		MoplUserDetails moplUserDetails,
		UUID lastEventId
	);
}
