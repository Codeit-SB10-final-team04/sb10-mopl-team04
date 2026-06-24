package com.team04.mopl.user.dto.response;

import java.util.UUID;

// 없으면 플레이리스트 구현에 진행이 거의 불가능해 미리 구현했습니다...
public record UserSummary(

	UUID userId,
	String name,
	String profileImageUrl
) {
}
