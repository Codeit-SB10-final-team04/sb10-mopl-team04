package com.team04.mopl.playlist.batch;

import java.time.ZoneId;

// Batch 작업에 필요한 공통 상수를 모아두는 유틸리티 클래스
public class PlaylistBatchTimeZone {

	public static final String ZONE_ID = "Asia/Seoul";
	public static final ZoneId KST = ZoneId.of(ZONE_ID);

	// 해당 클래스를 객체로 못만들게 설정
	private PlaylistBatchTimeZone() {
	}
}
