package com.team04.mopl.content.batch.sports;

/**
 * SportsDB에서 경기 상세 정보를 조회할 수 없을 때 던지는 예외.
 * Spring Batch Step 3의 skip 대상으로만 사용한다.
 */
public class EventDetailNotFoundException extends RuntimeException {

	public EventDetailNotFoundException(String eventId) {
		super("경기 상세 조회 결과 없음: eventId=" + eventId);
	}
}
