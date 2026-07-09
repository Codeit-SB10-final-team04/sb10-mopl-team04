package com.team04.mopl.user.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import com.team04.mopl.auth.security.MoplUserDetails;
import com.team04.mopl.user.dto.request.UserCreateRequest;
import com.team04.mopl.user.dto.request.UserLockUpdateRequest;
import com.team04.mopl.user.dto.request.UserPageRequest;
import com.team04.mopl.user.dto.request.UserRoleUpdateRequest;
import com.team04.mopl.user.dto.request.UserUpdateRequest;
import com.team04.mopl.user.dto.response.CursorResponseUserDto;
import com.team04.mopl.user.dto.response.UserDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "사용자 관리", description = "사용자 관리 API")
public interface UserControllerDocs {

	@Operation(summary = "사용자 등록 (회원가입)")
	ResponseEntity<UserDto> create(
		UserCreateRequest userCreateRequest
	);

	@Operation(summary = "프로필 변경", description = "본인의 프로필만 변경할 수 있습니다.")
	ResponseEntity<UserDto> updateProfile(
		UUID userId,
		UserUpdateRequest userUpdateRequest,
		MultipartFile image,
		MoplUserDetails moplUserDetails
	);

	@Operation(summary = "[어드민] 사용자 목록 조회 (커서 페이지네이션)")
	ResponseEntity<CursorResponseUserDto> findUsers(
		UserPageRequest userPageRequest
	);

	@Operation(summary = "[어드민] 권한 수정")
	ResponseEntity<Void> updateRole(
		UUID userId,
		UserRoleUpdateRequest userRoleUpdateRequest
	);

	@Operation(summary = "[어드민] 계정 잠금 상태 변경", description = "[어드민 기능] 계정 잠금 상태를 변경합니다.")
	ResponseEntity<Void> updateLocked(
		UUID userId,
		UserLockUpdateRequest userLockUpdateRequest
	);
}
