package com.team04.mopl.user.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.team04.mopl.auth.security.MoplUserDetails;
import com.team04.mopl.user.dto.request.ChangePasswordRequest;
import com.team04.mopl.user.dto.request.UserCreateRequest;
import com.team04.mopl.user.dto.request.UserLockUpdateRequest;
import com.team04.mopl.user.dto.request.UserPageRequest;
import com.team04.mopl.user.dto.request.UserRoleUpdateRequest;
import com.team04.mopl.user.dto.request.UserUpdateRequest;
import com.team04.mopl.user.dto.response.CursorResponseUserDto;
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

	// 프로필 변경
	@Override
	@PatchMapping(value = "/{userId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<UserDto> updateProfile(
		@PathVariable UUID userId,
		@Valid @RequestPart("request") UserUpdateRequest userUpdateRequest,
		@RequestPart(required = false) MultipartFile image,
		@AuthenticationPrincipal MoplUserDetails moplUserDetails
	) {
		UserDto userDto = userService.updateProfile(
			userId,
			userUpdateRequest,
			image,
			moplUserDetails.getUserId()
		);

		return ResponseEntity.status(HttpStatus.OK).body(userDto);
	}

	// 비밀번호 변경
	@Override
	@PatchMapping("/{userId}/password")
	public ResponseEntity<Void> updatePassword(
		@PathVariable UUID userId,
		@Valid @RequestBody ChangePasswordRequest changePasswordRequest,
		@AuthenticationPrincipal MoplUserDetails moplUserDetails
	) {
		userService.updatePassword(
			userId,
			changePasswordRequest,
			moplUserDetails.getUserId()
		);

		return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
	}

	// 사용자 상세 조회
	@Override
	@GetMapping("/{userId}")
	public ResponseEntity<UserDto> findById(
		@PathVariable UUID userId
	) {
		UserDto userDto = userService.findById(userId);

		return ResponseEntity.status(HttpStatus.OK).body(userDto);
	}

	// 관리자 사용자 목록 조회
	@Override
	@PreAuthorize("hasRole('ADMIN')")
	@GetMapping
	public ResponseEntity<CursorResponseUserDto> findUsers(
		@Valid @ModelAttribute UserPageRequest userPageRequest
	) {
		CursorResponseUserDto users = userAdminService.findUsers(userPageRequest);

		return ResponseEntity.status(HttpStatus.OK).body(users);
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

	// 관리자 계정 잠금 상태 변경
	@Override
	@PreAuthorize("hasRole('ADMIN')")
	@PatchMapping("/{userId}/locked")
	public ResponseEntity<Void> updateLocked(
		@PathVariable UUID userId,
		@Valid @RequestBody UserLockUpdateRequest userLockUpdateRequest
	) {
		userAdminService.updateLocked(userId, userLockUpdateRequest);

		return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
	}
}
