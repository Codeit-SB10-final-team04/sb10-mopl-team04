package com.team04.mopl.follow.entity;

import java.util.UUID;

import com.team04.mopl.common.entity.BaseEntity;
import com.team04.mopl.follow.exception.FollowErrorCode;
import com.team04.mopl.follow.exception.FollowException;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "follows")
public class Follow extends BaseEntity {

	/*
	TODO: User 생성 후 주석 제거 예정
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "followee_id", nullable = false)
	private User followee;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "follower_id", nullable = false)
	private User follower;

	@Builder
	public Follow(User followee, User follower) {
		validateSelfFollow(followee.getId(), follower.getId());

		this.followee = followee;
		this.follower = follower;
	}
	 */

	// 유효성 검증: 본인 팔로우 제외
	private void validateSelfFollow(UUID followeeId, UUID followerId) {
		if (followeeId.equals(followerId)) {
			throw new FollowException(FollowErrorCode.FOLLOW_SELF_NOT_ALLOWED)
				.addDetail("followeeId", followeeId)
				.addDetail("followerId", followerId);
		}
	}

}
