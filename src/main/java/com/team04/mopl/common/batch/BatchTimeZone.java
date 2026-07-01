package com.team04.mopl.common.batch;

import java.time.ZoneId;

// Batch 작업에 필요한 공통 시간대 상수
public class BatchTimeZone {

	public static final String ZONE_ID = "Asia/Seoul";
	public static final ZoneId KST = ZoneId.of(ZONE_ID);

	private BatchTimeZone() {
	}
}
