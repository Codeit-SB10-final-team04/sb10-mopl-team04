package com.team04.mopl.user.service;

import java.util.Map;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team04.mopl.auth.session.AuthSessionStore;
import com.team04.mopl.user.dto.request.UserLockUpdateRequest;
import com.team04.mopl.user.dto.request.UserPageRequest;
import com.team04.mopl.user.dto.request.UserRoleUpdateRequest;
import com.team04.mopl.user.dto.response.CursorResponseUserDto;
import com.team04.mopl.user.dto.response.UserCursorPage;
import com.team04.mopl.user.dto.response.UserDto;
import com.team04.mopl.user.entity.User;
import com.team04.mopl.user.entity.UserRole;
import com.team04.mopl.user.enums.UserSortBy;
import com.team04.mopl.user.event.UserRoleChangedEvent;
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
	private final ApplicationEventPublisher applicationEventPublisher;

	// 관리자 사용자 목록 조회
	public CursorResponseUserDto findUsers(UserPageRequest request) {
		// 이메일 검색어 정규화
		String normalizedEmailLike = request.normalizedEmailLike();

		// 사용자 목록 조회 요청 로그
		log.debug(
			"[USER_FIND_ALL] 사용자 목록 조회 시작: emailLikePresent={}, emailLikeLength={}, roleEqual={}, "
				+ "isLocked={}, cursorPresent={}, idAfterPresent={}, limit={}, sortDirection={}, sortBy={}",
			normalizedEmailLike != null,
			normalizedEmailLike == null ? 0 : normalizedEmailLike.length(),
			request.roleEqual(),
			request.isLocked(),
			request.cursor() != null,
			request.idAfter() != null,
			request.limit(),
			request.sortDirection(),
			request.sortBy()
		);

		// Querydsl 사용자 목록 조회
		UserCursorPage userCursorPage = userRepository.findUsers(request);

		// 다음 커서 계산 기준 사용자
		UserDto lastUser = userCursorPage.users().isEmpty()
			? null
			: userCursorPage.users().get(userCursorPage.users().size() - 1);

		// 다음 요청의 정렬 기준 값 커서
		String nextCursor = userCursorPage.hasNext() && lastUser != null
			? resolveNextCursor(lastUser, request.sortBy())
			: null;

		// 다음 요청의 동률 정렬 보조 커서
		UUID nextIdAfter = userCursorPage.hasNext() && lastUser != null
			? lastUser.id()
			: null;

		log.debug(
			"[USER_FIND_ALL] 사용자 목록 조회 완료: size={}, nextCursorPresent={}, nextIdAfterPresent={}, hasNext={}",
			userCursorPage.users().size(),
			nextCursor != null,
			nextIdAfter != null,
			userCursorPage.hasNext()
		);

		return new CursorResponseUserDto(
			userCursorPage.users(),
			nextCursor,
			nextIdAfter,
			userCursorPage.hasNext(),
			userCursorPage.totalCount(),
			request.sortBy().toString(),
			request.sortDirection()
		);
	}

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

		user.updateRole(newRole);

		// 권한이 바뀐 사용자의 기존 인증 세션 삭제
		authSessionStore.deleteByUserId(userId);

		applicationEventPublisher.publishEvent(
			UserRoleChangedEvent.of(userId, previousRole, newRole)
		);

		log.info(
			"[USER_ROLE_UPDATE] 사용자 권한 수정 및 인증 세션 삭제 완료: userId={}, previousRole={}, newRole={}",
			userId,
			previousRole,
			newRole
		);
	}

	// 관리자 계정 잠금 상태 변경
	@Transactional
	public void updateLocked(UUID userId, UserLockUpdateRequest request) {
		Boolean requestedLocked = request.locked();

		// 잠금 상태 필수값 검증
		validateLocked(requestedLocked);

		boolean newLocked = requestedLocked;
		log.info("[USER_LOCK_UPDATE] 계정 잠금 상태 변경 시작: userId={}, newLocked={}", userId, newLocked);

		User user = getUserOrThrow(userId);
		boolean previousLocked = user.isLocked();

		// 상태가 다를 때만 DB 변경
		if (previousLocked != newLocked) {
			user.updateLocked(newLocked);
		}

		// 잠금 요청 시 기존 인증 세션 삭제
		if (newLocked) {
			authSessionStore.deleteByUserId(userId);
		}

		log.info(
			"[USER_LOCK_UPDATE] 계정 잠금 상태 변경 완료: userId={}, previousLocked={}, newLocked={}, sessionDeleted={}",
			userId,
			previousLocked,
			newLocked,
			newLocked
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

	// 잠금 상태 필수값 검증
	private void validateLocked(Boolean locked) {
		if (locked == null) {
			throw new UserException(UserErrorCode.USER_LOCKED_REQUIRED);
		}
	}

	// 정렬 기준별 다음 커서 값 추출
	private String resolveNextCursor(UserDto userDto, UserSortBy sortBy) {
		return switch (sortBy) {
			case name -> userDto.name();
			case email -> userDto.email();
			case createdAt -> userDto.createdAt().toString();
			case isLocked -> userDto.locked().toString();
			case role -> userDto.role().toString();
		};
	}
}
