package com.team04.mopl.user.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.team04.mopl.user.dto.request.UserCreateRequest;
import com.team04.mopl.user.dto.request.UserRoleUpdateRequest;
import com.team04.mopl.user.dto.response.UserDto;
import com.team04.mopl.user.service.UserAdminService;
import com.team04.mopl.user.service.UserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController implements UserControllerDocs {

	private final UserService userService;
	private final UserAdminService userAdminService;

	// 회원가입
	@Override
	@PostMapping
	public ResponseEntity<UserDto> create(
		@Valid @RequestBody UserCreateRequest userCreateRequest
	) {
		UserDto userDto = userService.create(userCreateRequest);

		return ResponseEntity.status(HttpStatus.CREATED).body(userDto);
	}

	// 관리자 권한 수정
	@Override
	@PreAuthorize("hasRole('ADMIN')")
	@PatchMapping("/{userId}/role")
	public ResponseEntity<Void> updateRole(
		@PathVariable UUID userId,
		@Valid @RequestBody UserRoleUpdateRequest userRoleUpdateRequest
	) {
		userAdminService.updateRole(userId, userRoleUpdateRequest);

		return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
	}
}
