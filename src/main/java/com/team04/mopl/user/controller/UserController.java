package com.team04.mopl.user.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.team04.mopl.user.dto.request.UserCreateRequest;
import com.team04.mopl.user.dto.response.UserDto;
import com.team04.mopl.user.service.UserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController implements UserControllerDocs {

	private final UserService userService;

	// 회원가입
	@Override
	@PostMapping
	public ResponseEntity<UserDto> create(
		@Valid @RequestBody UserCreateRequest userCreateRequest
	) {
		UserDto userDto = userService.create(userCreateRequest);

		return ResponseEntity.status(HttpStatus.CREATED).body(userDto);
	}
}
