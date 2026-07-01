package com.team04.mopl.review.exception;

import org.springframework.http.HttpStatus;

import com.team04.mopl.common.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum ReviewErrorCode implements ErrorCode {
	REVIEW_ALREADY_EXISTS(HttpStatus.CONFLICT, "RV01", "이미 리뷰를 작성했습니다."),
	REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "RV02", "리뷰를 찾을 수 없습니다."),
	REVIEW_FORBIDDEN(HttpStatus.FORBIDDEN, "RV03", "리뷰에 대한 권한이 없습니다.");

	private final HttpStatus httpStatus;
	private final String code;
	private final String description;

	@Override
	public HttpStatus getHttpStatus() {
		return this.httpStatus;
	}

	@Override
	public String getCode() {
		return this.code;
	}

	@Override
	public String getMessage() {
		return this.description;
	}
}
