package com.team04.mopl.user.service;

import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team04.mopl.auth.session.AuthSessionStore;
import com.team04.mopl.user.dto.request.UserRoleUpdateRequest;
import com.team04.mopl.user.entity.User;
import com.team04.mopl.user.entity.UserRole;
import com.team04.mopl.user.exception.UserErrorCode;
import com.team04.mopl.user.exception.UserException;
import com.team04.mopl.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserAdminService {

	private final UserRepository userRepository;
	private final AuthSessionStore authSessionStore;

	// 관리자 권한 수정
	@Transactional
	public void updateRole(UUID userId, UserRoleUpdateRequest request) {
		UserRole newRole = request.role();

		log.info("[USER_ROLE_UPDATE] 사용자 권한 수정 시작: userId={}, newRole={}", userId, newRole);

		User user = getUserOrThrow(userId);
		UserRole previousRole = user.getRole();

		// 같은 권한으로 요청한 경우 DB 변경 및 세션 삭제 없이 종료
		if (previousRole == newRole) {
			log.info("[USER_ROLE_UPDATE] 사용자 권한 변경 없음: userId={}, role={}", userId, newRole);

			return;
		}

		// TODO: 알림 이벤트 발행
		user.updateRole(newRole);

		// 권한이 바뀐 사용자의 기존 인증 세션 삭제
		authSessionStore.deleteByUserId(userId);

		log.info(
			"[USER_ROLE_UPDATE] 사용자 권한 수정 및 인증 세션 삭제 완료: userId={}, previousRole={}, newRole={}",
			userId,
			previousRole,
			newRole
		);
	}

	// 관리자 기능에서 사용할 사용자 조회
	private User getUserOrThrow(UUID userId) {
		return userRepository.findById(userId)
			.orElseThrow(() -> new UserException(
				UserErrorCode.USER_NOT_FOUND,
				Map.of("userId", userId)
			));
	}
}
