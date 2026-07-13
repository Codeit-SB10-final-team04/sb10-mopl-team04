package com.team04.mopl.directmessage.event;

import java.util.UUID;

/*
	DirectMessageSentEvent
	----------------------
	메시지 전송 후, RDBMS와 ES 간의 데이터 동기화를 위해 발행하는 이벤트
 */
public record DirectMessageSentEvent(
	UUID conversationId,
	UUID messageId,
	String content
) {
}
