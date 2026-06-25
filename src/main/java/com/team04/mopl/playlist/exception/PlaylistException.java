package com.team04.mopl.playlist.exception;

import com.team04.mopl.common.exception.MoplException;

public class PlaylistException extends MoplException {

	public PlaylistException(PlaylistErrorCode errorCode) {
		super(errorCode);
	}

}
