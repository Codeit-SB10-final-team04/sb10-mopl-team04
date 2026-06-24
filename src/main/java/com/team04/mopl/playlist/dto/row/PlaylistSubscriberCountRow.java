package com.team04.mopl.playlist.dto.row;

import java.util.UUID;

// 플레이리스트 id별 구독자 수를 일괄 조회하기 위한 Repository 조회용 DTO
public record PlaylistSubscriberCountRow(

	UUID playlistId,
	long subscriberCount
) {
}
