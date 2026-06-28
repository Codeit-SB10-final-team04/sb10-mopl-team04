package com.team04.mopl.content.batch.step;

import org.springframework.batch.item.ItemProcessor;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.team04.mopl.content.client.SportsDbClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Step 3 Processor: eventId로 경기 상세를 조회해 JsonNode를 반환한다.
 * 상세 조회 실패 시 null 반환 → Spring Batch가 해당 아이템을 skip 처리한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventDetailItemProcessor implements ItemProcessor<String, JsonNode> {

	private final SportsDbClient sportsDbClient;

	@Override
	public JsonNode process(String eventId) {
		return sportsDbClient.getEventDetail(eventId)
			.orElseGet(() -> {
				log.warn("[Batch] 경기 상세 조회 결과 없음, skip: eventId={}", eventId);
				return null;
			});
	}
}
