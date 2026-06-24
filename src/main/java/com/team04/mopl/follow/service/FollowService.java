package com.team04.mopl.follow.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team04.mopl.auth.repository.AuthSessionRepository;
import com.team04.mopl.follow.dto.request.FollowRequest;
import com.team04.mopl.follow.exception.FollowErrorCode;
import com.team04.mopl.follow.exception.FollowException;
import com.team04.mopl.follow.mapper.FollowMapper;
import com.team04.mopl.follow.repository.FollowRepository;
import com.team04.mopl.user.entity.User;
import com.team04.mopl.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class FollowService {

	private final UserRepository userRepository;
	private final AuthSessionRepository authSessionRepository;
	private final FollowRepository followRepository;

	private final FollowMapper followMapper;

	// 팔로우 생성
	// @Transactional
	// public FollowDto createFollow(UUID sessionId, FollowRequest followRequest) {
	// 	// 1. 유효성 검증: 중복 검사
	// 	validateDuplicateFollow(followRequest.followeeId(), sessionId);
	//
	// 	// 3. 로그인 한 사용자 정보 조회 (Follower)
	// 	AuthSession session =
	//
	// 	// 2. 유효성 검증: 팔로우 대상 존재 여부
	// 	User targetFolloweeUser = getUserEntityOrThrow(followRequest.followeeId());
	//
	// 	// 4. 팔로우 생성
	// 	Follow newFollow = followMapper.toEntity(targetFolloweeUser, followerUser);
	//
	// 	// 5. 팔로워 저장
	// 	followRepository.save(newFollow);
	//
	// 	// 6. 로그 기록
	// 	log.info("Created follow with id: {}", newFollow.getId());
	//
	// 	return followMapper.toDto(newFollow);
	// }

	// 특정 유저의 팔로우 수 조회
	public Long getFollowerCount(FollowRequest followRequest) {
		// 1. 유효성 검증: 특정 사용자 존재 확인
		User targetUser = getUserEntityOrThrow(followRequest.followeeId());

		// 2. 특정 사용자의 팔로우 수 조회 및 반환
		return followRepository.countByFolloweeId(targetUser.getId());
	}

	// 유효성 검증: 팔로우 중복 검사 여부
	private void validateDuplicateFollow(UUID followeeId, UUID followerId) {
		if (followRepository.existsByFolloweeIdAndFollowerId(followeeId, followerId)) {
			throw new FollowException(FollowErrorCode.FOLLOW_ALREADY)
				.addDetail("followeeId", followeeId)
				.addDetail("followerId", followerId);
		}
	}

	// 사용자 엔티티 반환
	private User getUserEntityOrThrow(UUID userId) {
		return userRepository.findById(userId)
			// TODO: User 도메인의 최상위 예외 클래스 구현 시 주석 제거 예정
			.orElseThrow(/*() -> new Userxception(
				UserErrorCode
			)*/);
	}
}
