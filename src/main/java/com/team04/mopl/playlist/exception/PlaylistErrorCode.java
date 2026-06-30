package com.team04.mopl.playlist.exception;

import org.springframework.http.HttpStatus;

import com.team04.mopl.common.exception.ErrorCode;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum PlaylistErrorCode implements ErrorCode {

	PLAYLIST_INVALID_INPUT(HttpStatus.BAD_REQUEST, "PL01", "잘못된 입력값입니다."),
	PLAYLIST_NO_CHANGE_VALUE(HttpStatus.BAD_REQUEST, "PL02", "변경사항이 없습니다."),
	PLAYLIST_FORBIDDEN(HttpStatus.FORBIDDEN, "PL03", "플레이리스트 소유자가 아닙니다."),
	PLAYLIST_NOT_FOUND(HttpStatus.NOT_FOUND, "PL04", "플레이리스트를 찾을 수 없습니다."),
	PLAYLIST_UNSUBSCRIBED(HttpStatus.BAD_REQUEST, "PL05", "구독하지 않은 플레이리스트입니다."),
	PLAYLIST_ALREADY_SUBSCRIBED(HttpStatus.CONFLICT, "PL06", "이미 구독한 플레이리스트입니다."),
	PLAYLIST_SELF_SUBSCRIPTION_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "PL07", "본인이 생성한 플레이리스트는 구독할 수 없습니다."),
	PLAYLIST_SELF_UNSUBSCRIPTION_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "PL08", "본인이 생성한 플레이리스트는 구독 취소 할 수 없습니다."),
	PLAYLIST_CONTENT_ALREADY_ADD(HttpStatus.CONFLICT, "PL09", "이미 플레이리스트에 추가한 콘텐츠입니다."),
	PLAYLIST_CONTENT_NOT_FOUND(HttpStatus.NOT_FOUND, "PL10", "플레이리스트에 추가된 콘텐츠를 찾을 수 없습니다");

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
