package com.team04.mopl.playlist.exception;

import org.springframework.http.HttpStatus;

import com.team04.mopl.common.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum PlaylistErrorCode implements ErrorCode {

	INVALID_INPUT(HttpStatus.BAD_REQUEST, "PL01", "잘못된 입력값입니다."),
	NO_CHANGE_VALUE(HttpStatus.BAD_REQUEST, "PL02", "변경사항이 없습니다."),
	PLAYLIST_FORBIDDEN(HttpStatus.FORBIDDEN, "PL03", "플레이리스트 소유자가 아닙니다."),
	PLAYLIST_NOT_FOUND(HttpStatus.NOT_FOUND, "PL04", "플레이리스트를 찾을 수 없습니다."),
	PLAYLIST_ALREADY_SUBSCRIBED(HttpStatus.CONFLICT, "PL05", "이미 구독한 플레이리스트입니다."),
	PLAYLIST_HARD_DELETE_BATCH_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "PL06", "플레이리스트 물리 삭제 배치를 실패했습니다."),
	PLAYLIST_SELF_SUBSCRIPTION_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "PL07", "본인이 생성한 플레이리스트는 구독할 수 없습니다.");

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
