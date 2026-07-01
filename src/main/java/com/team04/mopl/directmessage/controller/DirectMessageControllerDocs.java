package com.team04.mopl.directmessage.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;

import com.team04.mopl.auth.security.MoplUserDetails;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Conversation API", description = "대화 API")
public interface DirectMessageControllerDocs {

	@Operation(
		summary = "DM 읽음 상태 생성",
		description = "사용자가 DM을 읽은 경우, 읽음 상태를 생성합니다."
	)
	ResponseEntity<Void> createDirectMessageReadStatus(
		UUID conversationId,
		UUID directMessageId,
		MoplUserDetails moplUserDetails
	);
}
