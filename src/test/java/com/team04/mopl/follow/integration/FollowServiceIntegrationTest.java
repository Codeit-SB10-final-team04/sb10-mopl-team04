package com.team04.mopl.follow.integration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

import com.team04.mopl.follow.dto.request.FollowRequest;
import com.team04.mopl.follow.dto.response.FollowDto;
import com.team04.mopl.follow.event.FollowCreatedEvent;
import com.team04.mopl.follow.event.FollowDeletedEvent;
import com.team04.mopl.follow.exception.FollowErrorCode;
import com.team04.mopl.follow.exception.FollowException;
import com.team04.mopl.follow.redis.FollowRedisStore;
import com.team04.mopl.follow.service.FollowService;
import com.team04.mopl.support.IntegrationTestBase;
import com.team04.mopl.user.entity.User;
import com.team04.mopl.user.repository.UserRepository;

@Transactional
@RecordApplicationEvents
class FollowServiceIntegrationTest extends IntegrationTestBase {

	@Autowired
	private FollowService followService;

	@Autowired
	private UserRepository userRepository;

	@MockitoBean
	private FollowRedisStore followRedisStore;

	@Autowired
	private ApplicationEvents applicationEvents;

	private User follower;
	private User followee;

	@BeforeEach
	void setUp() {
		follower = userRepository.save(User.builder()
			.name("팔로워")
			.email("follower@example.com")
			.build());

		followee = userRepository.save(User.builder()
			.name("팔로위")
			.email("followee@example.com")
			.build());
	}

	@Test
	@DisplayName("팔로우 생성 성공: 타겟 유저를 팔로우하고 이벤트가 발행된다.")
	void createFollow_Success() {
		// given
		FollowRequest request = new FollowRequest(followee.getId());

		// when
		FollowDto response = followService.createFollow(request, follower.getId());

		// then
		assertThat(response).isNotNull();
		assertThat(response.followeeId()).isEqualTo(followee.getId());

		long createdEventCount = applicationEvents.stream(FollowCreatedEvent.class).count();
		assertThat(createdEventCount).isEqualTo(1);
	}

	@Test
	@DisplayName("팔로우 중복 실패: 이미 팔로우한 유저를 다시 팔로우하면 예외가 발생한다.")
	void createFollow_Fail_Duplicate() {
		// given
		FollowRequest request = new FollowRequest(followee.getId());
		followService.createFollow(request, follower.getId());

		// when & then
		assertThatThrownBy(() -> followService.createFollow(request, follower.getId()))
			.isInstanceOf(FollowException.class)
			.hasMessageContaining(FollowErrorCode.FOLLOW_ALREADY.getMessage());
	}

	@Test
	@DisplayName("팔로워 수 조회 성공: Redis 캐시 미스 시 DB에서 백필하여 카운트를 반환한다.")
	void getFollowerCount_Success() {
		// given
		followService.createFollow(new FollowRequest(followee.getId()), follower.getId());
		when(followRedisStore.getFollowerCount(any())).thenReturn(null); // Cache Miss 강제

		// when
		Long count = followService.getFollowerCount(followee.getId());

		// then
		assertThat(count).isEqualTo(1L);
		verify(followRedisStore, times(1)).initFollowers(eq(followee.getId()), any());
	}

	@Test
	@DisplayName("팔로우 취소 성공: 본인이 생성한 팔로우 내역을 삭제한다.")
	void deleteFollow_Success() {
		// given
		FollowDto followDto = followService.createFollow(new FollowRequest(followee.getId()), follower.getId());

		// when
		followService.deleteFollow(followDto.id(), follower.getId());

		// then
		assertThatThrownBy(() -> followService.getFollowConnection(followee.getId(), follower.getId()))
			.isInstanceOf(FollowException.class);

		long deletedEventCount = applicationEvents.stream(FollowDeletedEvent.class).count();
		assertThat(deletedEventCount).isGreaterThanOrEqualTo(1);
	}

	@Test
	@DisplayName("팔로우 취소 실패: 타인의 팔로우 내역 삭제 시도 시 예외가 발생한다.")
	void deleteFollow_Fail_AccessDenied() {
		// given
		FollowDto followDto = followService.createFollow(new FollowRequest(followee.getId()), follower.getId());

		User unauthorizedUser = userRepository.save(User.builder()
			.name("외부인")
			.email("hacker@example.com")
			.build());

		// when & then
		assertThatThrownBy(() -> followService.deleteFollow(followDto.id(), unauthorizedUser.getId()))
			.isInstanceOf(FollowException.class)
			.hasMessageContaining(FollowErrorCode.FOLLOW_ACCESS_DENIED.getMessage());
	}
}