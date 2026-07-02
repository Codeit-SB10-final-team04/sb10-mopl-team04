package com.team04.mopl.review.batch;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.stereotype.Component;

import com.team04.mopl.review.repository.ReviewRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
@RequiredArgsConstructor
public class ReviewHardDeleteItemWriter implements ItemWriter<UUID> {

	private final ReviewRepository reviewRepository;

	@Override
	public void write(Chunk<? extends UUID> chunk) {
		List<UUID> ids = new ArrayList<>(chunk.getItems());
		if (ids.isEmpty()) {
			log.debug("[REVIEW_HARD_DELETE] 삭제 대상 없음, 스킵");
			return;
		}

		log.info("[REVIEW_HARD_DELETE] 리뷰 물리 삭제 시작: count={}", ids.size());
		reviewRepository.deleteAllByIdInBatch(ids);
		log.info("[REVIEW_HARD_DELETE] 리뷰 물리 삭제 완료: count={}", ids.size());
	}
}
