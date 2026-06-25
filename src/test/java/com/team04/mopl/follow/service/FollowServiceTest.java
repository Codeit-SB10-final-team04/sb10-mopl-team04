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
import org.springframework.dao.DataIntegrityViolationException;
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
		User follower = mock(User.class);
		given(follower.getId()).willReturn(currentUserId);

		UUID followeeId = UUID.randomUUID();
		User followee = mock(User.class);
		given(followee.getId()).willReturn(followeeId);
		FollowRequest request = new FollowRequest(followeeId);

		// 사용자 조회 모킹
		given(userRepository.findById(followeeId)).willReturn(Optional.of(followee));
		given(userRepository.findById(currentUserId)).willReturn(Optional.of(follower));

		// 중복 검사 모킹 (중복 아님)
		given(followRepository.existsByFolloweeIdAndFollowerId(followeeId, currentUserId)).willReturn(false);

		// 매퍼 모킹
		Follow mockFollow = mock(Follow.class);
		FollowDto mockDto = mock(FollowDto.class);
		given(followMapper.toEntity(followee, follower)).willReturn(mockFollow);
		given(followRepository.save(any(Follow.class))).willReturn(mockFollow);
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
	@DisplayName("실패: 본인을 팔로우하려고 하면 중복 조회 및 저장 없이 FollowException이 발생한다.")
	void createFollow_SelfFollow_Fail() {
		// given
		UUID currentUserId = UUID.randomUUID();
		FollowRequest request = new FollowRequest(currentUserId);
		User user = mock(User.class);

		given(user.getId()).willReturn(currentUserId);
		given(userRepository.findById(currentUserId)).willReturn(Optional.of(user));

		// when & then
		assertThatThrownBy(() -> followService.createFollow(request, currentUserId))
			.isInstanceOf(FollowException.class)
			.hasMessageContaining(FollowErrorCode.FOLLOW_SELF_NOT_ALLOWED.getMessage());

		// DB 중복 조회 및 저장 미발생 확인
		verify(followRepository, never()).existsByFolloweeIdAndFollowerId(any(), any());
		verify(followRepository, never()).save(any(Follow.class));
	}

	@Test
	@DisplayName("실패: 이미 팔로우 중인 대상이면 FollowException이 발생한다.")
	void createFollow_DuplicateFollow_Fail() {
		// given
		UUID currentUserId = UUID.randomUUID();
		User follower = mock(User.class);
		given(follower.getId()).willReturn(currentUserId);

		UUID followeeId = UUID.randomUUID();
		User followee = mock(User.class);
		given(followee.getId()).willReturn(followeeId);
		FollowRequest request = new FollowRequest(followeeId);

		given(userRepository.findById(followeeId)).willReturn(Optional.of(followee));
		given(userRepository.findById(currentUserId)).willReturn(Optional.of(follower));

		// 중복 검사 모킹 (이미 팔로우 중)
		given(followRepository.existsByFolloweeIdAndFollowerId(followeeId, currentUserId)).willReturn(true);

		// when & then
		assertThatThrownBy(() -> followService.createFollow(request, currentUserId))
			.isInstanceOf(FollowException.class)
			.hasMessageContaining(FollowErrorCode.FOLLOW_ALREADY.getMessage());
	}

	@Test
	@DisplayName("실패: 팔로우 생성 시 동시 요청으로 인한 DB 제약조건 위반 시 FollowException(FOLLOW_ALREADY_CONCURRENT)이 발생한다.")
	void createFollow_ConcurrentRequest_Fail() {
		// given
		UUID currentUserId = UUID.randomUUID();
		User follower = mock(User.class);
		given(follower.getId()).willReturn(currentUserId);

		UUID followeeId = UUID.randomUUID();
		User followee = mock(User.class);
		given(followee.getId()).willReturn(followeeId);
		FollowRequest request = new FollowRequest(followeeId);

		given(userRepository.findById(followeeId)).willReturn(Optional.of(followee));
		given(userRepository.findById(currentUserId)).willReturn(Optional.of(follower));

		given(followRepository.existsByFolloweeIdAndFollowerId(followeeId, currentUserId)).willReturn(false);

		Follow mockFollow = mock(Follow.class);
		given(followMapper.toEntity(any(), any())).willReturn(mockFollow);

		// 중복 요청으로 인한 DataIntegrityViolationException 발생
		given(followRepository.save(any(Follow.class)))
			.willThrow(new DataIntegrityViolationException("Duplicate entry"));

		// when & then
		assertThatThrownBy(() -> followService.createFollow(request, currentUserId))
			.isInstanceOf(FollowException.class)
			.hasMessageContaining(FollowErrorCode.FOLLOW_ALREADY_CONCURRENT.getMessage());

		// 첫 번째 요청만 저장
		verify(followRepository, times(1)).save(any(Follow.class));
	}

	/*
	=============================
		특정 사용자 팔로우 여부 조회
	=============================
	 */
	@Test
	@DisplayName("성공: 두 사용자가 존재하고 팔로우 관계가 있다면 FollowDto를 반환한다.")
	void isFollowing_Success() {
		// given
		UUID currentUserId = UUID.randomUUID();
		User requestedUser = mock(User.class);
		given(requestedUser.getId()).willReturn(currentUserId);

		UUID followeeId = UUID.randomUUID();
		User targetUser = mock(User.class);
		given(targetUser.getId()).willReturn(followeeId);

		given(userRepository.findById(followeeId)).willReturn(Optional.of(targetUser));
		given(userRepository.findById(currentUserId)).willReturn(Optional.of(requestedUser));

		Follow mockFollow = mock(Follow.class);
		given(mockFollow.getId()).willReturn(UUID.randomUUID());
		given(followRepository.findByFolloweeIdAndFollowerId(followeeId, currentUserId))
			.willReturn(Optional.of(mockFollow));

		FollowDto mockDto = mock(FollowDto.class);
		given(followMapper.toDto(mockFollow)).willReturn(mockDto);

		// when
		FollowDto result = followService.isFollowing(followeeId, currentUserId);

		// then
		assertThat(result).isNotNull();
		assertThat(result).isEqualTo(mockDto);
		verify(followRepository, times(1)).findByFolloweeIdAndFollowerId(followeeId, currentUserId);
	}

	// @Test
	// @DisplayName("실패: 팔로우 대상(Followee)이 존재하지 않으면 예외가 발생한다.")
	// void isFollowing_UserNotFound_Fail() {
	// 	// given
	// 	UUID currentUserId = UUID.randomUUID();
	// 	UUID followeeId = UUID.randomUUID();
	//
	// 	// 타겟 유저 조회 시 Optional.empty() 반환
	// 	given(userRepository.findById(followeeId)).willReturn(Optional.empty());
	//
	// 	// when & then
	// 	// TODO: 향후 User 도메인 예외 도입 시 해당 예외 타입으로 검증 강화
	// 	assertThatThrownBy(() -> followService.isFollowing(followeeId, currentUserId))
	// 		.isInstanceOf();
	//
	// 	// 유저가 없으면 팔로우 조회 쿼리가 실행되지 않아야 함 (조기 차단 검증)
	// 	verify(followRepository, never()).findByFolloweeIdAndFollowerId(any(), any());
	// }

	@Test
	@DisplayName("실패: 팔로우 관계가 존재하지 않으면 FOLLOW_NOT_FOUND 예외가 발생한다.")
	void isFollowing_FollowNotFound_Fail() {
		// given
		UUID currentUserId = UUID.randomUUID();
		User requestedUser = mock(User.class);
		given(requestedUser.getId()).willReturn(currentUserId);

		UUID followeeId = UUID.randomUUID();
		User targetUser = mock(User.class);
		given(targetUser.getId()).willReturn(followeeId);

		given(userRepository.findById(followeeId)).willReturn(Optional.of(targetUser));
		given(userRepository.findById(currentUserId)).willReturn(Optional.of(requestedUser));

		// 팔로우 미존재
		given(followRepository.findByFolloweeIdAndFollowerId(followeeId, currentUserId))
			.willReturn(Optional.empty());

		// when & then
		assertThatThrownBy(() -> followService.isFollowing(followeeId, currentUserId))
			.isInstanceOf(FollowException.class)
			.hasMessageContaining(FollowErrorCode.FOLLOW_NOT_FOUND.getMessage());
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