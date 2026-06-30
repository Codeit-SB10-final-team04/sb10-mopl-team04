package com.team04.mopl.content.batch.step;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

import com.team04.mopl.content.repository.ContentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// Chunk 단위로 콘텐츠를 DB에서 물리 삭제하는 Writer
@Component
@Slf4j
@RequiredArgsConstructor
public class ContentHardDeleteItemWriter implements ItemWriter<UUID> {

	private final ContentRepository contentRepository;

	@Override
	public void write(Chunk<? extends UUID> chunk) {
		List<UUID> ids = new ArrayList<>(chunk.getItems());
		if (ids.isEmpty()) {
			return;
		}

		contentRepository.deleteAllByIdInBatch(ids);

		log.info("[CONTENT_HARD_DELETE_BATCH] 콘텐츠 chunk 물리 삭제: count={}", ids.size());
	}
}
