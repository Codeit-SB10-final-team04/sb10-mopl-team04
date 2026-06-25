package com.team04.mopl.follow.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.team04.mopl.follow.dto.request.FollowRequest;
import com.team04.mopl.follow.dto.response.FollowDto;
import com.team04.mopl.follow.entity.Follow;
import com.team04.mopl.follow.exception.FollowErrorCode;
import com.team04.mopl.follow.exception.FollowException;
import com.team04.mopl.follow.mapper.FollowMapper;
import com.team04.mopl.follow.repository.FollowRepository;
import com.team04.mopl.user.entity.User;
import com.team04.mopl.user.repository.UserRepository;

// import com.team04.mopl.user.exception.UserErrorCode;
// import com.team04.mopl.user.exception.UserException;

@ExtendWith(MockitoExtension.class)
class FollowServiceTest {

	@InjectMocks
	private FollowService followService;

	@Mock
	private UserRepository userRepository;

	@Mock
	private FollowRepository followRepository;

	@Mock
	private FollowMapper followMapper;

	/*
	=========================
		팔로우 생성
	=========================
	 */
	@Test
	@DisplayName("성공: 유효한 요청일 경우 팔로우가 정상적으로 생성된다.")
	void createFollow_Success() {
		// given
		UUID currentUserId = UUID.randomUUID();
		UUID followeeId = UUID.randomUUID();
		FollowRequest request = new FollowRequest(followeeId);

		User follower = mock(User.class);
		User followee = mock(User.class);
		given(follower.getId()).willReturn(currentUserId);
		given(followee.getId()).willReturn(followeeId);

		// 사용자 조회 모킹
		given(userRepository.findById(followeeId)).willReturn(Optional.of(followee));
		given(userRepository.findById(currentUserId)).willReturn(Optional.of(follower));

		// 중복 검사 모킹 (중복 아님)
		given(followRepository.existsByFolloweeIdAndFollowerId(followeeId, currentUserId)).willReturn(false);

		// 매퍼 모킹
		Follow mockFollow = mock(Follow.class);
		FollowDto mockDto = mock(FollowDto.class);
		given(followMapper.toEntity(followee, follower)).willReturn(mockFollow);
		given(followMapper.toDto(mockFollow)).willReturn(mockDto);

		// when
		FollowDto result = followService.createFollow(request, currentUserId);

		// then
		assertThat(result).isNotNull();
		verify(followRepository, times(1)).save(mockFollow);
	}

	// @Test
	// @DisplayName("실패: 팔로우 대상(Followee)이 존재하지 않으면 예외가 발생한다.")
	// void createFollow_FolloweeNotFound_Fail() {
	// 	// given
	// 	UUID currentUserId = UUID.randomUUID();
	// 	UUID followeeId = UUID.randomUUID();
	// 	FollowRequest request = new FollowRequest(followeeId);
	//
	// 	given(userRepository.findById(followeeId)).willReturn(Optional.empty());
	//
	// 	// when & then
	// 	// TODO: User 도메인의 최상위 예외 클래스 구현 시 주석 제거 예정
	// 	assertThatThrownBy(() -> followService.createFollow(request, currentUserId))
	// 		.isInstanceOf(/*UserException.class*/);
	// }

	@Test
	@DisplayName("실패: 이미 팔로우 중인 대상이면 FollowException이 발생한다.")
	void createFollow_DuplicateFollow_Fail() {
		// given
		UUID currentUserId = UUID.randomUUID();
		UUID followeeId = UUID.randomUUID();
		FollowRequest request = new FollowRequest(followeeId);

		User follower = mock(User.class);
		User followee = mock(User.class);

		given(userRepository.findById(followeeId)).willReturn(Optional.of(followee));
		given(userRepository.findById(currentUserId)).willReturn(Optional.of(follower));

		// 중복 검사 모킹 (이미 팔로우 중)
		given(followRepository.existsByFolloweeIdAndFollowerId(followeeId, currentUserId)).willReturn(true);

		// when & then
		assertThatThrownBy(() -> followService.createFollow(request, currentUserId))
			.isInstanceOf(FollowException.class)
			.hasMessageContaining(FollowErrorCode.FOLLOW_ALREADY.getMessage());
	}

	/*
	=============================
		특정 사용자의 팔로우 수 조회
	=============================
	 */
	@Test
	@DisplayName("성공: 유저가 존재하면 팔로워 수를 정상적으로 반환한다.")
	void getFollowerCount_ReturnFollowerCount_Success() {
		// given
		UUID followeeId = UUID.randomUUID();

		User mockUser = User.builder()
			.name("테스트유저")
			.email("test@example.com")
			.build();
		ReflectionTestUtils.setField(mockUser, "id", followeeId);
		long expectedCount = 15L;

		given(userRepository.findById(followeeId)).willReturn(Optional.of(mockUser));
		given(followRepository.countByFolloweeId(followeeId)).willReturn(expectedCount);

		// when
		Long result = followService.getFollowerCount(followeeId);

		// then
		assertThat(result).isEqualTo(expectedCount);
		verify(userRepository).findById(followeeId);
		verify(followRepository).countByFolloweeId(followeeId);
	}

	// @Test
	// @DisplayName("실패: 조회하려는 유저가 존재하지 않으면 예외가 발생한다.")
	// void getFollowerCount_UserNotFound_ThrowException_Fail() {
	// 	// given
	// 	UUID invalidUserId = UUID.randomUUID();
	// 	FollowRequest request = new FollowRequest(invalidUserId);
	//
	// 	given(userRepository.findById(invalidUserId)).willReturn(Optional.empty());
	//
	// 	// when & then
	// 	assertThatThrownBy(() -> followService.getFollowerCount(request))
	// 		.isInstanceOf(UserException.class)
	// 		.hasMessageContaining(UserErrorCode.USER_NOT_FOUND.getMessage());
	// }
}