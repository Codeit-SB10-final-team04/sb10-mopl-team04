package com.team04.mopl.content.batch.sports;

import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.team04.mopl.content.client.SportsDbClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Step 3 Processor: eventId로 경기 상세를 조회해 JsonNode를 반환한다.
 * 상세 조회 실패 시 {@link EventDetailNotFoundException}을 던져 skip 처리한다.
 * (null 반환은 filtered item으로 처리되어 processSkipCount가 증가하지 않으므로 예외 사용)
 *
 * <p>API 응답에 idEvent가 누락된 경우, Reader에서 받은 eventId를 직접 주입해 Writer까지 전달한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventDetailItemProcessor implements ItemProcessor<String, JsonNode> {

	private final SportsDbClient sportsDbClient;

	@Override
	public JsonNode process(String eventId) {
		JsonNode detail = sportsDbClient.getEventDetail(eventId)
			.orElseThrow(() -> {
				log.warn("[Batch] 경기 상세 조회 결과 없음, skip: eventId={}", eventId);
				return new EventDetailNotFoundException(eventId);
			});

		// API 응답에 idEvent가 없거나 비어있으면 Reader에서 받은 eventId를 주입
		if (!StringUtils.hasText(detail.path("idEvent").asText(""))) {
			log.warn("[Batch] 응답에 idEvent 누락, Reader의 eventId로 보완: eventId={}", eventId);
			((ObjectNode)detail).put("idEvent", eventId);
		}

		return detail;
	}
}
