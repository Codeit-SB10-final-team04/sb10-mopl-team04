package com.team04.mopl.sse.event;

// SSE 이벤트 이름을 관리하는 상수 클래스
// 클라이언트가 구독하는 이벤트 이름과 서버에서 전송하는 이벤트 이름이 일치해야 하기에 문자열이 아닌 상수로 관리
// 한 번 초기화한 값을 변경하지 않게 하기 위해 final 사용
public final class SseEventNames {

	public static final String NOTIFICATIONS = "notifications";
	public static final String DIRECT_MESSAGES = "direct-messages";

	private SseEventNames() {
	}
}
