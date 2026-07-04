package com.team04.mopl.sse.dto;

import java.util.UUID;

public record SseMessage(

	UUID id,
	UUID receiverId,
	String eventName,
	Object data
) {
}
