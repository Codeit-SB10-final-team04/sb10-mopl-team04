package com.team04.mopl.playlist.exception;

import org.springframework.http.HttpStatus;

import com.team04.mopl.common.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum PlaylistErrorCode implements ErrorCode {

	INVALID_INPUT(HttpStatus.BAD_REQUEST, "PL01", "잘못된 입력값입니다."),
	NO_CHANGE_VALUE(HttpStatus.BAD_REQUEST, "PL02", "변경사항이 없습니다."),
	PLAYLIST_FORBIDDEN(HttpStatus.FORBIDDEN, "PL03", "플레이리스트 소유자가 아닙니다."),
	PLAYLIST_NOT_FOUND(HttpStatus.NOT_FOUND, "PL04", "플레이리스트를 찾을 수 없습니다.");

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
