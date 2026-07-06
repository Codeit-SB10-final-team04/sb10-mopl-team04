package com.team04.mopl.watching.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.team04.mopl.common.dto.CursorResponse;
import com.team04.mopl.watching.dto.request.WatchingSessionPageRequest;
import com.team04.mopl.watching.dto.response.WatchingSessionDto;
import com.team04.mopl.watching.service.WatchingSessionService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class WatchingSessionController implements WatchingSessionControllerDocs {

	private final WatchingSessionService watchingSessionService;

	@Override
	@GetMapping("/contents/{contentId}/watching-sessions")
	public ResponseEntity<CursorResponse<WatchingSessionDto>> findByContent(
		@PathVariable UUID contentId,
		@Valid @ModelAttribute WatchingSessionPageRequest request
	) {
		CursorResponse<WatchingSessionDto> response = watchingSessionService.findByContentId(contentId, request);

		return ResponseEntity.ok(response);
	}

	@Override
	@GetMapping("/users/{watcherId}/watching-sessions")
	public ResponseEntity<WatchingSessionDto> findByWatcher(@PathVariable UUID watcherId) {
		return watchingSessionService.findByWatcherId(watcherId)
			.map(ResponseEntity::ok)
			.orElse(ResponseEntity.ok().build());
	}
}
