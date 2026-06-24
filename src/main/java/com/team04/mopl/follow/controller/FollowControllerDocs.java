package com.team04.mopl.follow.controller;

import org.springframework.http.ResponseEntity;

import com.team04.mopl.follow.dto.request.FollowRequest;
import com.team04.mopl.follow.dto.response.FollowDto;

public interface FollowControllerDocs {
	// 팔로우 생성
	// TODO: 메서드를 요청한 사용자 정보가 필요 -> 임시로 CustomUserDetails로 구현하되, 주석 처리함
	ResponseEntity<FollowDto> createFollow(FollowRequest followRequest/*, CustomUserDetails userDetails */);
}
