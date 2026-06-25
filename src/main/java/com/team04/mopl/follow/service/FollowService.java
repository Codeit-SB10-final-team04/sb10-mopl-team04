package com.team04.mopl.follow.service;

import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
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

		// 1. 유효성 검증: 로그인 사용자(팔로워) 및 팔로우 대상 존재 여부
		User followeeUser = getUserEntityOrThrow(followRequest.followeeId());
		User followerUser = getUserEntityOrThrow(currentUserId);

		// 2. 유효성 검증: 본인 팔로우 검사
		validateSelfFollow(followeeUser.getId(), followerUser.getId());

		// 3. 유효성 검증: 중복 팔로우 검사
		validateDuplicateFollow(followeeUser.getId(), followerUser.getId());

		// 4. 팔로우 생성 및 저장
		// TODO: 분산 환경에서의 동시성 이슈를 해결하기 위한 Redis 분산 락(Redisson 등) 적용 예정 (심화)
		// 분산 락 적용 시, DB 제약조건 예외를 잡는 현재의 catch 블록은 제거 후 로직 개선
		try {
			Follow newFollow = followMapper.toEntity(followeeUser, followerUser);
			followRepository.save(newFollow);

			log.info("[FOLLOW_CREATE] 팔로우 생성 완료: followId={}, followeeId={}, followerId={}",
				newFollow.getId(), followeeUser.getId(), followerUser.getId());

			return followMapper.toDto(newFollow);
		} catch (DataIntegrityViolationException e) {
			// DB 제약조건 위반 시, 이미 중복인 상황으로 간주
			throw new FollowException(FollowErrorCode.FOLLOW_ALREADY_CONCURRENT)
				.addDetail("followeeId", followeeUser.getId())
				.addDetail("followerId", followerUser.getId());
		}
	}

	// 사용자의 특정 사용자 팔로우 여부 조회
	public FollowDto getFollowConnection(UUID followeeId, UUID currentUserId) {
		log.debug("[FOLLOW_FIND_IS_FOLLWING] 특정 사용자 팔로우 여부 조회 시작: followeeId={}, userId={}",
			followeeId, currentUserId);

		// 1. 유효성 검증: 요청자와 특정 사용자 존재 여부
		User targetUser = getUserEntityOrThrow(followeeId);          // 특정 사용자
		User requestedUser = getUserEntityOrThrow(currentUserId);    // 요청자

		// 2. 팔로우 여부 조회
		Follow followConnection = getFollowEntityByFolloweeIdAndFollowerIdOrThrow(targetUser.getId(),
			requestedUser.getId());

		log.debug("[FOLLOW_FIND_IS_FOLLWING] 특정 사용자 팔로우 여부 조회 완료: followId={}. followeeId={}, userId={}",
			followConnection.getId(), followeeId, currentUserId);

		return followMapper.toDto(followConnection);
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

	// 팔로우 취소
	@Transactional
	public void deleteFollow(UUID followId, UUID currentUserId) {
		log.info("[FOLLOW_DELETE] 팔로우 취소 시작: followId={}", followId);

		// 1. 유효성 검증: 팔로우 및 사용자 존재
		Follow targetFollow = getFollowEntityOrThrow(followId);
		User requestedUser = getUserEntityOrThrow(currentUserId);

		// 2. 유효성 검증: 팔로우 소유자
		validateFollowOwner(targetFollow.getId(), requestedUser.getId());

		// 3. 팔로우 삭제 (Hard Delete)
		followRepository.delete(targetFollow);

		log.info("[FOLLOW_DELETE] 팔로우 취소 완료: followId={}", followId);
	}

	// 유효성 검증: 팔로우 중복 검사
	private void validateDuplicateFollow(UUID followeeId, UUID followerId) {
		if (followRepository.existsByFolloweeIdAndFollowerId(followeeId, followerId)) {
			throw new FollowException(FollowErrorCode.FOLLOW_ALREADY)
				.addDetail("followeeId", followeeId)
				.addDetail("followerId", followerId);
		}
	}

	// 유효성 검증: 본인 팔로우 검사
	private void validateSelfFollow(UUID followeeId, UUID followerId) {
		if (followerId.equals(followeeId)) {
			throw new FollowException(FollowErrorCode.FOLLOW_SELF_NOT_ALLOWED)
				.addDetail("followeeId", followeeId)
				.addDetail("followerId", followerId);
		}
	}

	// 유효성 검증: 팔로우 소유자 검사
	private void validateFollowOwner(UUID followId, UUID followerId) {
		if (!followRepository.existsByIdAndFollowerId(followId, followerId)) {
			throw new FollowException(FollowErrorCode.FOLLOW_ACCESS_DENIED)
				.addDetail("followId", followId)
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

	// 팔로우 엔티티 반환 (팔로우 Id)
	private Follow getFollowEntityOrThrow(UUID followId) {
		return followRepository.findById(followId)
			.orElseThrow(() -> new FollowException(FollowErrorCode.FOLLOW_NOT_FOUND));
	}

	// 팔로우 엔티티 반환 (팔로위 Id, 팔로워 Id)
	private Follow getFollowEntityByFolloweeIdAndFollowerIdOrThrow(UUID followeeId, UUID followerId) {
		return followRepository.findByFolloweeIdAndFollowerId(followeeId, followerId)
			.orElseThrow(() -> new FollowException(FollowErrorCode.FOLLOW_NOT_FOUND));
	}
}
