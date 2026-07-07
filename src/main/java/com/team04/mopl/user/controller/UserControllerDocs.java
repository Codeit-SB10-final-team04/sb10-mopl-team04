package com.team04.mopl.user.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;

import com.team04.mopl.user.dto.request.UserCreateRequest;
import com.team04.mopl.user.dto.request.UserRoleUpdateRequest;
import com.team04.mopl.user.dto.response.UserDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "사용자 관리", description = "사용자 관리 API")
public interface UserControllerDocs {

	@Operation(summary = "사용자 등록 (회원가입)")
	ResponseEntity<UserDto> create(
		UserCreateRequest userCreateRequest
	);

	@Operation(summary = "[어드민] 권한 수정")
	ResponseEntity<Void> updateRole(
		UUID userId,
		UserRoleUpdateRequest userRoleUpdateRequest
	);
}
