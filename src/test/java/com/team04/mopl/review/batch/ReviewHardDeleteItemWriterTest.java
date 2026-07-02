package com.team04.mopl.review.batch;

import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.item.Chunk;

import com.team04.mopl.review.repository.ReviewRepository;

@ExtendWith(MockitoExtension.class)
class ReviewHardDeleteItemWriterTest {

	@Mock
	private ReviewRepository reviewRepository;

	@InjectMocks
	private ReviewHardDeleteItemWriter writer;

	@Test
	@DisplayName("chunk가 비어있으면 deleteAllByIdInBatch를 호출하지 않는다")
	void write_doesNotDelete_whenChunkIsEmpty() throws Exception {
		writer.write(new Chunk<>(Collections.emptyList()));

		verify(reviewRepository, never()).deleteAllByIdInBatch(any());
	}

	@Test
	@DisplayName("chunk에 ID가 있으면 deleteAllByIdInBatch를 호출한다")
	void write_deletesAllIds_whenChunkIsNotEmpty() throws Exception {
		List<UUID> ids = List.of(UUID.randomUUID(), UUID.randomUUID());

		writer.write(new Chunk<>(ids));

		verify(reviewRepository).deleteAllByIdInBatch(ids);
	}
}
