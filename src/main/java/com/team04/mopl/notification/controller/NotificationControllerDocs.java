package com.team04.mopl.notification.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;

import com.team04.mopl.auth.security.MoplUserDetails;
import com.team04.mopl.notification.dto.request.NotificationSearchRequest;
import com.team04.mopl.notification.dto.response.CursorResponseNotificationDto;

import io.swagger.v3.oas.annotations.Operation;

public interface NotificationControllerDocs {

	@Operation(summary = "알림 목록 조회 (커서 페이지네이션)", description = "API 요청자의 알림 목록만 조회할 수 있습니다.")
	ResponseEntity<CursorResponseNotificationDto> findAllNotifications(
		NotificationSearchRequest request,
		MoplUserDetails moplUserDetails
	);

	@Operation(summary = "알림 읽음 처리")
	ResponseEntity<Void> readNotification(
		UUID notificationId,
		MoplUserDetails moplUserDetails
	);
}
