package com.team04.mopl.follow.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.team04.mopl.auth.security.MoplUserDetails;
import com.team04.mopl.follow.dto.request.FollowRequest;
import com.team04.mopl.follow.dto.response.FollowDto;
import com.team04.mopl.follow.service.FollowService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/follows")
public class FollowController implements FollowControllerDocs {

	private final FollowService followService;

	@Override
	@PostMapping
	public ResponseEntity<FollowDto> createFollow(
		@Valid @RequestBody FollowRequest followRequest,
		@AuthenticationPrincipal MoplUserDetails moplUserDetails
	) {

		FollowDto followDto = followService.createFollow(followRequest, moplUserDetails.getUserId());

		return ResponseEntity.status(HttpStatus.CREATED).body(followDto);
	}

	@Override
	@GetMapping("/followed-by-me")
	public ResponseEntity<FollowDto> getFollowConnection(
		@RequestParam UUID followeeId,
		@AuthenticationPrincipal MoplUserDetails moplUserDetails
	) {

		FollowDto followDto = followService.getFollowConnection(followeeId, moplUserDetails.getUserId());

		return ResponseEntity.status(HttpStatus.OK).body(followDto);
	}

	@Override
	@GetMapping("/count")
	public ResponseEntity<Long> getFollowerCount(@RequestParam UUID followeeId) {

		Long followerCount = followService.getFollowerCount(followeeId);

		return ResponseEntity.status(HttpStatus.OK).body(followerCount);
	}

	@Override
	@DeleteMapping("/{followId}")
	public ResponseEntity<Void> deleteFollow(
		@PathVariable UUID followId,
		@AuthenticationPrincipal MoplUserDetails moplUserDetails
	) {

		followService.deleteFollow(followId, moplUserDetails.getUserId());

		return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
	}
}
