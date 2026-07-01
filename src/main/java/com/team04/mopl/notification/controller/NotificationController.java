package com.team04.mopl.notification.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.team04.mopl.auth.security.MoplUserDetails;
import com.team04.mopl.notification.dto.request.NotificationPageRequest;
import com.team04.mopl.notification.dto.response.CursorResponseNotificationDto;
import com.team04.mopl.notification.service.NotificationService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController implements NotificationControllerDocs {

	private final NotificationService notificationService;

	@GetMapping
	@Override
	public ResponseEntity<CursorResponseNotificationDto> findAllNotifications(
		@Valid @ModelAttribute NotificationPageRequest request,
		@AuthenticationPrincipal MoplUserDetails moplUserDetails
	) {
		UUID currentUserId = moplUserDetails.getUserId();
		CursorResponseNotificationDto cursorResponseNotificationDto =
			notificationService.findAllNotifications(request, currentUserId);

		return ResponseEntity.status(HttpStatus.OK).body(cursorResponseNotificationDto);
	}

	@DeleteMapping(value = "/{notificationId}")
	@Override
	public ResponseEntity<Void> readNotification(
		@PathVariable UUID notificationId,
		@AuthenticationPrincipal MoplUserDetails moplUserDetails
	) {
		UUID currentUserId = moplUserDetails.getUserId();
		notificationService.readNotification(notificationId, currentUserId);

		return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
	}
}
