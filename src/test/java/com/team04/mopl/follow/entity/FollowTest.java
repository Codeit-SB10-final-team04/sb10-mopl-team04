package com.team04.mopl.follow.entity;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.team04.mopl.follow.exception.FollowErrorCode;
import com.team04.mopl.follow.exception.FollowException;
import com.team04.mopl.user.entity.User;

class FollowTest {

	@Test
	@DisplayName("성공: 서로 다른 사용자 간의 팔로우 객체가 정상적으로 생성된다.")
	void createFollow_Success() {
		// given
		User followee = mock(User.class);
		given(followee.getId()).willReturn(UUID.randomUUID());

		User follower = mock(User.class);
		given(follower.getId()).willReturn(UUID.randomUUID());

		// when
		Follow follow = Follow.builder()
			.followee(followee)
			.follower(follower)
			.build();

		// then
		assertThat(follow.getFollowee()).isEqualTo(followee);
		assertThat(follow.getFollower()).isEqualTo(follower);
	}

	@Test
	@DisplayName("실패: followee가 null이면 NullPointerException이 발생한다.")
	void createFollow_NullFollowee_Fail() {
		// given
		User follower = mock(User.class);

		// when & then
		assertThatThrownBy(() -> Follow.builder()
			.followee(null)
			.follower(follower)
			.build())
			.isInstanceOf(NullPointerException.class)
			.hasMessageContaining("팔로우 대상은 필수입니다");
	}

	@Test
	@DisplayName("실패: follower가 null이면 NullPointerException이 발생한다.")
	void createFollow_NullFollower_Fail() {
		// given
		User followee = mock(User.class);

		// when & then
		assertThatThrownBy(() -> Follow.builder()
			.followee(followee)
			.follower(null)
			.build())
			.isInstanceOf(NullPointerException.class)
			.hasMessageContaining("팔로워는 필수입니다");
	}

	@Test
	@DisplayName("실패: 객체는 다르지만 ID가 동일한 경우 본인 팔로우로 간주하여 예외가 발생한다.")
	void createFollow_DifferentObjectsSameId_Fail() {
		// given
		UUID sameId = UUID.randomUUID();

		// 1. 첫 번째 유저 객체
		User user1 = mock(User.class);
		given(user1.getId()).willReturn(sameId);

		// 2. 두 번째 유저 객체 (user1과 인스턴스는 다르지만 ID는 동일)
		User user2 = mock(User.class);
		given(user2.getId()).willReturn(sameId);

		// when & then
		assertThatThrownBy(() -> Follow.builder()
			.followee(user1)
			.follower(user2)
			.build())
			.isInstanceOf(FollowException.class)
			.hasMessageContaining(FollowErrorCode.FOLLOW_SELF_NOT_ALLOWED.getMessage());
	}
}
