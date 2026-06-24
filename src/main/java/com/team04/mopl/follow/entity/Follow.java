package com.team04.mopl.follow.entity;

import java.util.Objects;
import java.util.UUID;

import com.team04.mopl.common.entity.BaseEntity;
import com.team04.mopl.user.entity.User;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
	name = "follows",
	uniqueConstraints = {
		@UniqueConstraint(
			name = "uk_follow_followee_follower",
			columnNames = {"followee_id", "follower_id"}
		)
	}
)
public class Follow extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "followee_id", nullable = false)
	private User followee;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "follower_id", nullable = false)
	private User follower;

	@Builder
	public Follow(User followee, User follower) {
		Objects.requireNonNull(followee, "팔로우 대상은 필수입니다.");
		Objects.requireNonNull(follower, "팔로워는 필수입니다.");

		validateSelfFollow(followee.getId(), follower.getId());

		this.followee = followee;
		this.follower = follower;
	}

	// 유효성 검증: 본인 팔로우 제외
	private void validateSelfFollow(UUID followeeId, UUID followerId) {
		if (followeeId.equals(followerId)) {
			// TODO: 팔로우 생성 브랜치에서 해당 예외 구현 완료 -> 이후 PR에서 주석 제거 및 병합 예정
			// throw new FollowException(FollowErrorCode.FOLLOW_SELF_NOT_ALLOWED)
			// 	.addDetail("followeeId", followeeId)
			// 	.addDetail("followerId", followerId);
		}
	}

}
