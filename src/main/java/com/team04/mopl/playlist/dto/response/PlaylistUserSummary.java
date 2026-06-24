package com.team04.mopl.playlist.dto.response;

import java.util.UUID;

// TODO: UserSummary 구현 후 삭제
public record PlaylistUserSummary(

	UUID userId,
	String name,
	String profileImageUrl
) {
}
