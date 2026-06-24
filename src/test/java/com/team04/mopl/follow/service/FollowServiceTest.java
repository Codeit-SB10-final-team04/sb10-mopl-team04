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
import com.team04.mopl.follow.repository.FollowRepository;
import com.team04.mopl.user.entity.EmailType;
import com.team04.mopl.user.entity.User;
// import com.team04.mopl.user.exception.UserErrorCode;
// import com.team04.mopl.user.exception.UserException;
import com.team04.mopl.user.entity.UserRole;
import com.team04.mopl.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class FollowServiceTest {

	@InjectMocks
	private FollowService followService;

	@Mock
	private UserRepository userRepository;

	@Mock
	private FollowRepository followRepository;

	@Test
	@DisplayName("성공: 유저가 존재하면 팔로워 수를 정상적으로 반환한다.")
	void getFollowerCount_ReturnFollowerCount_Success() {
		// given
		UUID followeeId = UUID.randomUUID();
		FollowRequest request = new FollowRequest(followeeId);

		User mockUser = User.builder()
			.name("테스트유저")
			.email("test@example.com")
			.emailType(EmailType.REAL)
			.role(UserRole.USER)
			.locked(false)
			.build();
		ReflectionTestUtils.setField(mockUser, "id", followeeId);
		long expectedCount = 15L;

		given(userRepository.findById(followeeId)).willReturn(Optional.of(mockUser));
		given(followRepository.countByFolloweeId(followeeId)).willReturn(expectedCount);

		// when
		Long result = followService.getFollowerCount(request);

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