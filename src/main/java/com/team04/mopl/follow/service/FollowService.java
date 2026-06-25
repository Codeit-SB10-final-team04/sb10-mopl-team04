package com.team04.mopl.follow.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team04.mopl.follow.dto.request.FollowRequest;
import com.team04.mopl.follow.dto.response.FollowDto;
import com.team04.mopl.follow.entity.Follow;
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
	private final FollowRepository followRepository;

	private final FollowMapper followMapper;

	// 팔로우 생성
	@Transactional
	public FollowDto createFollow(FollowRequest followRequest, UUID currentUserId) {
		log.info("[FOLLOW_CREATE] 팔로우 생성 시작: followeeId={}, followerId={}",
			followRequest.followeeId(), currentUserId);

		// 1. 유효성 검증: 로그인 사용자 및 팔로우 대상 존재 여부
		User followeeUser = getUserEntityOrThrow(followRequest.followeeId());
		User followerUser = getUserEntityOrThrow(currentUserId);

		// 2. 유효성 검증: 중복 팔로우 검사
		validateDuplicateFollow(followRequest.followeeId(), currentUserId);

		// 3. 팔로우 생성 및 저장
		Follow newFollow = followMapper.toEntity(followeeUser, followerUser);
		followRepository.save(newFollow);

		log.info("[FOLLOW_CREATE] 팔로우 생성 완료: followId={}, followeeId={}, followerId={}",
			newFollow.getId(), followeeUser.getId(), followerUser.getId());

		return followMapper.toDto(newFollow);
	}

	// 특정 유저의 팔로우 수 조회
	public Long getFollowerCount(UUID followeeId) {
		log.debug("[FOLLOW_FIND_COUNT] 특정 사용자의 팔로우 수 조회 시작: followeeId={}", followeeId);

		// 1. 유효성 검증: 특정 사용자 존재 확인
		User targetUser = getUserEntityOrThrow(followeeId);

		// 2. 특정 사용자의 팔로우 수 조회
		Long followCount = followRepository.countByFolloweeId(targetUser.getId());

		log.debug("[FOLLOW_FIND_COUNT] 특정 사용자의 팔로우 수 조회 완료: followeeId={}, followCount={}", followeeId, followCount);

		return followCount;
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
