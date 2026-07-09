package com.team04.mopl.watching.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;

import com.team04.mopl.common.dto.CursorResponse;
import com.team04.mopl.watching.dto.request.WatchingSessionPageRequest;
import com.team04.mopl.watching.dto.response.WatchingSessionDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "시청 세션 관리", description = "시청 세션 API")
public interface WatchingSessionControllerDocs {

	@Operation(summary = "특정 콘텐츠의 시청 세션 목록 조회 (커서 페이지네이션)")
	ResponseEntity<CursorResponse<WatchingSessionDto>> findByContent(
		UUID contentId,
		WatchingSessionPageRequest request
	);

	@Operation(summary = "특정 사용자의 시청 세션 조회 (nullable)")
	ResponseEntity<WatchingSessionDto> findByWatcher(UUID watcherId);
}
