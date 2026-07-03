package com.team04.mopl.user.repository.qdsl;

import static com.team04.mopl.user.entity.QUser.user;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.StringExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.team04.mopl.common.enums.SortDirection;
import com.team04.mopl.user.dto.request.UserPageRequest;
import com.team04.mopl.user.dto.response.UserCursorPage;
import com.team04.mopl.user.dto.response.UserDto;
import com.team04.mopl.user.entity.UserRole;
import com.team04.mopl.user.enums.UserSortBy;
import com.team04.mopl.user.exception.UserErrorCode;
import com.team04.mopl.user.exception.UserException;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class UserQdslRepositoryImpl implements UserQdslRepository {

	private final JPAQueryFactory jpaQueryFactory;

	@Override
	public UserCursorPage findUsers(UserPageRequest request) {
		int limit = request.limit();

		// 사용자 목록 DTO 조회
		List<UserDto> rows = jpaQueryFactory
			// 응답에 필요한 사용자 컬럼 projection
			.select(Projections.constructor(
				UserDto.class,
				user.id,
				user.createdAt,
				user.email,
				user.name,
				user.profileImageUrl,
				user.role,
				user.locked
			))
			.from(user)
			// 선택 필터와 커서 조건 적용
			.where(
				emailLike(request.normalizedEmailLike()),
				roleEq(request.roleEqual()),
				lockedEq(request.isLocked()),
				cursorCondition(
					request.cursor(),
					request.sortBy(),
					request.sortDirection(),
					request.idAfter()
				)
			)
			// 정렬 기준과 보조 정렬 기준 적용
			.orderBy(orderCondition(request.sortBy(), request.sortDirection()))
			// 다음 페이지 판단용 추가 조회
			.limit(limit + 1)
			.fetch();

		// 다음 페이지 존재 여부 판단
		boolean hasNext = rows.size() > limit;

		// 실제 응답 목록 절단
		List<UserDto> users = hasNext
			? rows.subList(0, limit)
			: rows;

		// 커서 조건 제외 전체 개수 조회
		Long totalCount = getTotalCount(request);

		// 내부 커서 페이지 결과 반환
		return new UserCursorPage(
			users,
			hasNext,
			totalCount == null ? 0L : totalCount
		);
	}

	// 이메일 부분 검색 조건
	private BooleanExpression emailLike(String emailLike) {
		if (emailLike == null) {
			return null;
		}

		return user.email.containsIgnoreCase(emailLike);
	}

	// 권한 일치 조건
	private BooleanExpression roleEq(UserRole role) {
		if (role == null) {
			return null;
		}

		return user.role.eq(role);
	}

	// 계정 잠금 상태 일치 조건
	private BooleanExpression lockedEq(Boolean locked) {
		if (locked == null) {
			return null;
		}

		return user.locked.eq(locked);
	}

	// 정렬 기준별 커서 조건 분기
	private BooleanExpression cursorCondition(
		String cursor,
		UserSortBy sortBy,
		SortDirection sortDirection,
		UUID idAfter
	) {
		if (cursor == null || idAfter == null) {
			return null;
		}

		// 정렬 기준에 맞는 커서 비교 조건
		return switch (sortBy) {
			case name -> stringCursor(user.name, cursor, sortDirection, idAfter);
			case email -> stringCursor(user.email, cursor, sortDirection, idAfter);
			case createdAt -> createdAtCursor(cursor, sortDirection, idAfter);
			case isLocked -> lockedCursor(cursor, sortDirection, idAfter);
			case role -> roleCursor(cursor, sortDirection, idAfter);
		};
	}

	// 문자열 정렬값 커서 조건
	private BooleanExpression stringCursor(
		StringExpression path,
		String cursor,
		SortDirection sortDirection,
		UUID idAfter
	) {
		if (isDescending(sortDirection)) {
			// 내림차순 문자열 커서 조건
			return path.lt(cursor)
				.or(path.eq(cursor).and(user.id.lt(idAfter)));
		}

		// 오름차순 문자열 커서 조건
		return path.gt(cursor)
			.or(path.eq(cursor).and(user.id.gt(idAfter)));
	}

	// 생성일 정렬값 커서 조건
	private BooleanExpression createdAtCursor(
		String cursor,
		SortDirection sortDirection,
		UUID idAfter
	) {
		// 생성일 커서 문자열 변환
		Instant cursorCreatedAt = parseInstant(cursor);

		if (isDescending(sortDirection)) {
			// 내림차순 생성일 커서 조건
			return user.createdAt.lt(cursorCreatedAt)
				.or(user.createdAt.eq(cursorCreatedAt).and(user.id.lt(idAfter)));
		}

		// 오름차순 생성일 커서 조건
		return user.createdAt.gt(cursorCreatedAt)
			.or(user.createdAt.eq(cursorCreatedAt).and(user.id.gt(idAfter)));
	}

	// 계정 잠금 상태 정렬값 커서 조건
	private BooleanExpression lockedCursor(
		String cursor,
		SortDirection sortDirection,
		UUID idAfter
	) {
		// 계정 잠금 상태 커서 문자열 변환
		Boolean cursorLocked = parseBoolean(cursor);

		if (isDescending(sortDirection)) {
			if (cursorLocked) {
				// 내림차순 true 구간 커서 조건
				return user.locked.isTrue().and(user.id.lt(idAfter))
					.or(user.locked.isFalse());
			}

			// 내림차순 false 구간 커서 조건
			return user.locked.isFalse().and(user.id.lt(idAfter));
		}

		if (cursorLocked) {
			// 오름차순 true 구간 커서 조건
			return user.locked.isTrue().and(user.id.gt(idAfter));
		}

		// 오름차순 false 구간 커서 조건
		return user.locked.isFalse().and(user.id.gt(idAfter))
			.or(user.locked.isTrue());
	}

	// 권한 정렬값 커서 조건
	private BooleanExpression roleCursor(
		String cursor,
		SortDirection sortDirection,
		UUID idAfter
	) {
		// 권한 커서 문자열 변환
		UserRole cursorRole = parseRole(cursor);

		// 권한 문자열 기준 커서 조건 재사용
		return stringCursor(
			user.role.stringValue(),
			cursorRole.name(),
			sortDirection,
			idAfter
		);
	}

	// 정렬 조건 생성
	private OrderSpecifier<?>[] orderCondition(
		UserSortBy sortBy,
		SortDirection sortDirection
	) {
		// 내림차순 여부
		boolean descending = isDescending(sortDirection);

		// 주 정렬 조건
		OrderSpecifier<?> sortOrder = switch (sortBy) {
			case name -> descending ? user.name.desc() : user.name.asc();
			case email -> descending ? user.email.desc() : user.email.asc();
			case createdAt -> descending ? user.createdAt.desc() : user.createdAt.asc();
			case isLocked -> descending ? user.locked.desc() : user.locked.asc();
			case role -> descending ? user.role.desc() : user.role.asc();
		};

		// 주 정렬 조건과 ID 보조 정렬 조건
		return new OrderSpecifier<?>[] {
			sortOrder,
			descending ? user.id.desc() : user.id.asc()
		};
	}

	// 필터 조건 기준 전체 개수 조회
	private Long getTotalCount(UserPageRequest request) {
		return jpaQueryFactory
			.select(user.count())
			.from(user)
			.where(
				emailLike(request.normalizedEmailLike()),
				roleEq(request.roleEqual()),
				lockedEq(request.isLocked())
			)
			.fetchOne();
	}

	// 내림차순 여부 판단
	private boolean isDescending(SortDirection sortDirection) {
		return sortDirection == SortDirection.DESCENDING;
	}

	// Instant 커서 변환
	private Instant parseInstant(String cursor) {
		try {
			return Instant.parse(cursor);
		} catch (DateTimeParseException exception) {
			throw invalidCursorException(cursor, exception);
		}
	}

	// Boolean 커서 변환
	private Boolean parseBoolean(String cursor) {
		if ("true".equalsIgnoreCase(cursor)) {
			return true;
		}

		if ("false".equalsIgnoreCase(cursor)) {
			return false;
		}

		throw invalidCursorException(cursor, null);
	}

	// UserRole 커서 변환
	private UserRole parseRole(String cursor) {
		try {
			return UserRole.valueOf(cursor);
		} catch (IllegalArgumentException exception) {
			throw invalidCursorException(cursor, exception);
		}
	}

	// 잘못된 커서 예외 생성
	private UserException invalidCursorException(String cursor, Throwable cause) {
		UserException exception = cause == null
			? new UserException(UserErrorCode.USER_INVALID_CURSOR)
			: new UserException(UserErrorCode.USER_INVALID_CURSOR, cause);

		return (UserException)exception
			.addDetail("cursor", cursor)
			.addDetail("message", "정렬 기준에 맞지 않는 커서입니다.");
	}
}
