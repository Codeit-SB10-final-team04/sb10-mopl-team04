package com.team04.mopl.user.dto.request;

import java.util.UUID;

import org.springframework.util.StringUtils;

import com.team04.mopl.common.enums.SortDirection;
import com.team04.mopl.user.entity.UserRole;
import com.team04.mopl.user.enums.UserSortBy;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UserPageRequest(

	// 이메일 부분 검색 조건
	String emailLike,

	// 권한 일치 필터
	UserRole roleEqual,

	// 계정 잠금 상태 필터
	Boolean isLocked,

	// 정렬 기준 값 커서
	String cursor,

	// 동률 정렬 보조 커서
	UUID idAfter,

	// 페이지 조회 개수
	@NotNull(message = "조회할 데이터 개수는 필수입니다.")
	@Min(value = 1, message = "조회할 데이터 개수는 1개 이상이어야 합니다.")
	@Max(value = 100, message = "조회할 데이터 개수는 100개를 초과할 수 없습니다.")
	Integer limit,

	// 정렬 방향
	@NotNull(message = "정렬 방향은 필수입니다.")
	SortDirection sortDirection,

	// 정렬 기준
	@NotNull(message = "정렬 기준은 필수입니다.")
	UserSortBy sortBy
) {

	// 이메일 검색어 공백 제거
	public String normalizedEmailLike() {
		return StringUtils.hasText(emailLike)
			? emailLike.strip()
			: null;
	}

	// 커서와 보조 커서 동시 요청 검증
	@AssertTrue(message = "cursor와 idAfter는 함께 요청되어야 합니다.")
	public boolean isCursorAndIdAfterPaired() {
		return (cursor == null) == (idAfter == null);
	}
}
