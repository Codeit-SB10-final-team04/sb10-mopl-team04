package com.team04.mopl.watching.store;

import java.time.Instant;
import java.util.UUID;

// 시청 세션의 식별 정보
// 입장 시점에 한 번 생성되어 Store에 저장되므로, 조회할 때마다 동일한 id/joinedAt이 유지됨
// (커서 페이지네이션의 idAfter 매칭과 createdAt 정렬이 안정적으로 동작하기 위해 필요)
public record WatchingSessionInfo(
	UUID id,
	Instant joinedAt
) {
	public static WatchingSessionInfo create() {
		return new WatchingSessionInfo(UUID.randomUUID(), Instant.now());
	}
}
