package com.team04.mopl.content.batch.step;

import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.team04.mopl.content.service.MatchCollectService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Step 3 Writer: chunk 단위로 경기 상세 데이터를 DB에 저장한다.
 * 트랜잭션은 chunk 단위로 묶이므로 중간 실패 시 해당 chunk만 롤백된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventDetailItemWriter implements ItemWriter<JsonNode> {

	private final MatchCollectService matchCollectService;

	@Override
	public void write(Chunk<? extends JsonNode> chunk) {
		int saved = 0;
		int skipped = 0;

		for (JsonNode detail : chunk) {
			String eventName = detail.path("strEvent").asText("");
			boolean isSaved = matchCollectService.saveIfNotExists(detail, eventName);
			if (isSaved) {
				saved++;
				log.debug("[Batch] 경기 저장: {}", eventName);
			} else {
				skipped++;
			}
		}

		log.info("[Batch] chunk 처리 완료: 저장 {}건, 건너뜀 {}건", saved, skipped);
	}
}
