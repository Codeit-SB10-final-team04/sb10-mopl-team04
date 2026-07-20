package com.team04.mopl.support;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public final class CapturingSseEmitter extends SseEmitter {

	private final List<Object> sentData =
		new CopyOnWriteArrayList<>();

	@Override
	public void send(SseEventBuilder builder) throws IOException {
		builder.build()
			.forEach(item -> sentData.add(item.getData()));
	}

	public boolean containsEvent(
		UUID eventId,
		String eventName
	) {
		String expectedId = "id:" + eventId;
		String expectedName = "event:" + eventName;

		return sentData.stream()
			.filter(o -> o instanceof String)
			.map(o -> (String)o)
			.anyMatch(value ->
				value.contains(expectedId)
					&& value.contains(expectedName)
			);
	}

	public boolean containsData(Object expectedData) {
		return sentData.contains(expectedData);
	}
}