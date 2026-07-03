package com.team04.mopl.directmessage.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;

import com.team04.mopl.auth.security.MoplUserDetails;
import com.team04.mopl.directmessage.dto.request.DirectMessagePagedRequest;
import com.team04.mopl.directmessage.dto.response.CursorResponseDirectMessageDto;

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

	@Operation(
		summary = "DM 목록 조회",
		description = "특정 대화방에서 발행된 다이렉트 메시지 목록을 조회합니다."
	)
	ResponseEntity<CursorResponseDirectMessageDto> findAll(
		UUID conversationId,
		DirectMessagePagedRequest directMessagePagedRequest,
		MoplUserDetails moplUserDetails
	);
}
