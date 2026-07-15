package com.team04.mopl.follow.service;

import java.util.Set;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team04.mopl.follow.dto.request.FollowRequest;
import com.team04.mopl.follow.dto.response.FollowDto;
import com.team04.mopl.follow.entity.Follow;
import com.team04.mopl.follow.event.FollowCreatedEvent;
import com.team04.mopl.follow.event.FollowDeletedEvent;
import com.team04.mopl.follow.exception.FollowErrorCode;
import com.team04.mopl.follow.exception.FollowException;
import com.team04.mopl.follow.mapper.FollowMapper;
import com.team04.mopl.follow.redis.FollowRedisStore;
import com.team04.mopl.follow.repository.FollowRepository;
import com.team04.mopl.user.entity.User;
import com.team04.mopl.user.exception.UserErrorCode;
import com.team04.mopl.user.exception.UserException;
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

	private final FollowRedisStore followRedisStore;

	private final ApplicationEventPublisher applicationEventPublisher;

	// 팔로우 생성
	@Transactional
	@PreAuthorize("#followRequest.followeeId() != #requestUserId")
	public FollowDto createFollow(
		FollowRequest followRequest,
		UUID requestUserId
	) {
		log.info("[FOLLOW_CREATE] 팔로우 생성 시작: followeeId={}, followerId={}",
			followRequest.followeeId(), requestUserId);

		// 1. 유효성 검증: 로그인 사용자(팔로워) 및 팔로우 대상 존재 여부
		User followeeUser = getUserEntityOrThrow(followRequest.followeeId());
		User followerUser = getUserEntityOrThrow(requestUserId);

		// 2. 유효성 검증: 중복 팔로우 여부
		validateDuplicateFollow(followeeUser.getId(), followerUser.getId());

		// 3. 팔로우 생성 및 저장
		Follow newFollow = followMapper.toEntity(followeeUser, followerUser);
		followRepository.save(newFollow);

		// 4. 팔로우 생성 이벤트 발행: Redis 저장 및 알림
		applicationEventPublisher.publishEvent(
			FollowCreatedEvent.of(
				followeeUser.getId(),
				followeeUser.getName(),
				followerUser.getId(),
				followerUser.getName()
			)
		);

		log.info("[FOLLOW_CREATE] 팔로우 생성 완료: followId={}, followeeId={}, followerId={}",
			newFollow.getId(), followeeUser.getId(), followerUser.getId());

		return followMapper.toDto(newFollow);
	}

	// 사용자의 특정 사용자 팔로우 여부 조회
	public FollowDto getFollowConnection(
		UUID followeeId,
		UUID requestUserId
	) {
		log.debug("[FOLLOW_FIND_IS_FOLLWING] 특정 사용자 팔로우 여부 조회 시작: followeeId={}, requestUserId={}",
			followeeId, requestUserId);

		// 1. 유효성 검증: 요청자와 특정 사용자 존재 여부
		User targetUser = getUserEntityOrThrow(followeeId);          // 특정 사용자
		User requestedUser = getUserEntityOrThrow(requestUserId);    // 요청자

		// 2. 팔로우 여부 조회 (Redis)
		boolean isFollowConnection = followRedisStore.isFollowing(requestUserId, followeeId);

		// 3. 팔로우 조회 (RDB)
		Follow followConnection = getFollowEntityByFolloweeIdAndFollowerIdOrThrow(
			targetUser.getId(),
			requestedUser.getId()
		);

		// 4, 팔로우 여부 조회 (DB FallBack)
		if (!isFollowConnection) {
			followRedisStore.addFollow(requestUserId, followeeId);
		}

		log.debug("[FOLLOW_FIND_IS_FOLLWING] 특정 사용자 팔로우 여부 조회 완료: followId={}. followeeId={}, requestUserId={}",
			followConnection.getId(), followeeId, requestUserId);

		return followMapper.toDto(followConnection);
	}

	// 특정 유저의 팔로우 수 조회
	public Long getFollowerCount(UUID followeeId) {
		log.debug("[FOLLOW_FIND_COUNT] 특정 사용자의 팔로우 수 조회 시작: followeeId={}",
			followeeId);

		// 1. 유효성 검증: 특정 사용자 존재 확인
		getUserEntityOrThrow(followeeId);

		// 2. 특정 사용자의 팔로우 수 조회
		Long followCount = followRedisStore.getFollowerCount(followeeId);

		// 3. 특정 사용자의 팔로우 수 조회 (DB FallBack)
		if (followCount == null) {
			Set<UUID> followerIds = followRepository.findFollowerIdsByFolloweeId(followeeId);
			followRedisStore.initFollowers(followeeId, followerIds);
			followCount = (long)followerIds.size();
		}

		log.debug("[FOLLOW_FIND_COUNT] 특정 사용자의 팔로우 수 조회 완료: followeeId={}, followCount={}",
			followeeId, followCount);

		return followCount;
	}

	// 팔로우 취소
	@Transactional
	public void deleteFollow(
		UUID followId,
		UUID requestUserId
	) {
		log.info("[FOLLOW_DELETE] 팔로우 취소 시작: followId={}",
			followId);

		// 1. 유효성 검증: 팔로우 존재
		Follow targetFollow = getFollowEntityOrThrow(followId);

		// 2. 유효성 검증: 팔로우 소유자
		validateFollowOwner(targetFollow, requestUserId);

		// 3. 팔로위 및 팔로워 ID 추출
		UUID followeeId = targetFollow.getFollowee().getId();
		UUID followerId = targetFollow.getFollower().getId();

		// 4. 팔로우 삭제 (Hard Delete)
		followRepository.delete(targetFollow);

		// 5. Redis 삭제 이벤트 발행
		applicationEventPublisher.publishEvent(
			new FollowDeletedEvent(
				followeeId,
				followerId
			)
		);

		log.info("[FOLLOW_DELETE] 팔로우 취소 완료: followId={}",
			followId);
	}

	// 유효성 검증: 팔로우 중복 검사
	private void validateDuplicateFollow(UUID followeeId, UUID followerId) {
		if (followRepository.existsByFolloweeIdAndFollowerId(followeeId, followerId)) {
			throw new FollowException(FollowErrorCode.FOLLOW_ALREADY)
				.addDetail("followeeId", followeeId)
				.addDetail("followerId", followerId);
		}
	}

	// 유효성 검증: 팔로우 소유자 검사
	private void validateFollowOwner(Follow follow, UUID requestUserId) {
		if (!follow.getFollower().getId().equals(requestUserId)) {
			throw new FollowException(FollowErrorCode.FOLLOW_ACCESS_DENIED)
				.addDetail("followOwnerId", follow.getFollower().getId())
				.addDetail("requestUserId", requestUserId);
		}
	}

	// 사용자 엔티티 반환
	private User getUserEntityOrThrow(UUID userId) {
		return userRepository.findById(userId)
			.orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND)
				.addDetail("userId", userId));
	}

	// 팔로우 엔티티 반환 (팔로우 Id)
	private Follow getFollowEntityOrThrow(UUID followId) {
		return followRepository.findById(followId)
			.orElseThrow(() -> new FollowException(FollowErrorCode.FOLLOW_NOT_FOUND)
				.addDetail("followId", followId));
	}

	// 팔로우 엔티티 반환 (팔로위 Id, 팔로워 Id)
	private Follow getFollowEntityByFolloweeIdAndFollowerIdOrThrow(UUID followeeId, UUID followerId) {
		return followRepository.findByFolloweeIdAndFollowerId(followeeId, followerId)
			.orElseThrow(() -> new FollowException(FollowErrorCode.FOLLOW_NOT_FOUND)
				.addDetail("followeeId", followeeId)
				.addDetail("followerId", followerId));
	}
}
