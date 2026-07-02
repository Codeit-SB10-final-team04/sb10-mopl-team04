package com.team04.mopl.conversation.dto.request;

import java.util.UUID;

import com.team04.mopl.common.enums.SortDirection;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ConversationPageRequest(
	// 검색 키워드: 사용자 이름, 메시지 내용
	String keywordLike,

	// 메인 커서
	String cursor,

	// 보조 커서
	UUID idAfter,

	// 페이지 개수
	@NotNull(message = "조회하고자 하는 대화방 개수를 입력해주세요.")
	@Positive(message = "조회하고자 하는 대화방 개수는 양수이어야 합니다.")
	// TODO: 상향선 지정 예정
	Integer limit,

	// 정렬 방향
	@NotNull(message = "정렬 방향을 선택해주세요.")
	SortDirection sortDirection,

	// 정렬 기준
	@NotBlank(message = "정렬 기준을 선택해주세요.")
	String sortBy
) {

	// TODO: 기본값 설정하는 생성자 추가 예정
	// 기본값 설정을 위한 생성자
}
