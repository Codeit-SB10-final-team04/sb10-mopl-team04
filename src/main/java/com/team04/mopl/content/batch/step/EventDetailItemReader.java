package com.team04.mopl.content.batch.step;

import java.util.List;

import org.springframework.batch.item.ItemReader;

import lombok.extern.slf4j.Slf4j;

/**
 * Step 3 Reader: JobContext의 eventIds에서 경기 ID를 하나씩 반환한다.
 * null 반환 시 Spring Batch가 읽기 완료로 판단한다.
 */
@Slf4j
public class EventDetailItemReader implements ItemReader<String> {

	private final List<String> eventIds;
	private int index = 0;

	public EventDetailItemReader(List<String> eventIds) {
		this.eventIds = eventIds != null ? eventIds : List.of();
		log.info("[Batch] EventDetailItemReader 초기화: 총 {}건", this.eventIds.size());
	}

	@Override
	public String read() {
		if (index < eventIds.size()) {
			return eventIds.get(index++);
		}
		return null;
	}
}
