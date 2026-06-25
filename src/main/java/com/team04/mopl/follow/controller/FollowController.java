package com.team04.mopl.follow.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.team04.mopl.follow.service.FollowService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/follows")
public class FollowController implements FollowControllerDocs {

	private final FollowService followService;

	// @Override
	// @PostMapping
	// public ResponseEntity<FollowDto> createFollow(
	// 	@Valid @RequestBody FollowRequest followRequest/*t,
	// 	TODO: 메서드를 요청한 사용자 정보가 필요 -> 임시로 CustomUserDetails로 구현하되, 주석 처리함
	// 	@AuthenticationPrincipal MoplUserDetails moplUserDetails*/
	// ) {
	// 	FollowDto response = followService.createFollow(followRequest/*, userDetails*/);
	//
	// 	return ResponseEntity.status(HttpStatus.CREATED).body(response);
	// }

	@Override
	@GetMapping("/count")
	public ResponseEntity<Long> getFollowerCount(@RequestParam UUID followeeId) {

		Long followerCount = followService.getFollowerCount(followeeId);

		return ResponseEntity.status(HttpStatus.OK).body(followerCount);
	}
}
