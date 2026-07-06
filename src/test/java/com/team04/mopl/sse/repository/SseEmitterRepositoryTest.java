package com.team04.mopl.sse.repository;

import static org.assertj.core.api.Assertions.*;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class SseEmitterRepositoryTest {

	private final SseEmitterRepository sseEmitterRepository = new SseEmitterRepository();

	@Test
	@DisplayName("같은 수신자에게 여러 SseEmitter를 등록할 수 있다.")
	void add_saveMultiSseEmitter_whenSameReceiver() {
		// given
		UUID receiverId = UUID.randomUUID();
		SseEmitter sseEmitter1 = new SseEmitter();
		SseEmitter sseEmitter2 = new SseEmitter();

		// when
		sseEmitterRepository.add(receiverId, sseEmitter1);
		sseEmitterRepository.add(receiverId, sseEmitter2);

		// then
		assertThat(sseEmitterRepository.findAllByReceiverId(receiverId))
			.containsExactly(sseEmitter1, sseEmitter2);
	}

	@Test
	@DisplayName("특정 SseEmitter만 제거하면 같은 수신자의 다른 SseEmitter는 유지된다.")
	void remove_deleteOnlyRequestedSseEmitter_whenMultiSseEmitterExist() {
		// given
		UUID receiverId = UUID.randomUUID();
		SseEmitter sseEmitter1 = new SseEmitter();
		SseEmitter sseEmitter2 = new SseEmitter();

		sseEmitterRepository.add(receiverId, sseEmitter1);
		sseEmitterRepository.add(receiverId, sseEmitter2);

		// when
		sseEmitterRepository.remove(receiverId, sseEmitter1);

		// then
		assertThat(sseEmitterRepository.findAllByReceiverId(receiverId))
			.containsExactly(sseEmitter2);
		assertThat(sseEmitterRepository.findAll())
			.containsKey(receiverId);
	}

	@Test
	@DisplayName("수신자의 마지막 SseEmitter를 제거하면 수신자의 key도 삭제한다.")
	void remove_deleteReceiverKey_whenLastSseEmitterRemoved() {
		// given
		UUID receiverId = UUID.randomUUID();
		SseEmitter sseEmitter = new SseEmitter();

		sseEmitterRepository.add(receiverId, sseEmitter);

		// when
		sseEmitterRepository.remove(receiverId, sseEmitter);

		// then
		assertThat(sseEmitterRepository.findAllByReceiverId(receiverId))
			.isEmpty();
		assertThat(sseEmitterRepository.findAll())
			.doesNotContainKey(receiverId);
	}

	@Test
	@DisplayName("등록되지 않은 수신자가 SseEmitter 목록을 조회하면 빈 리스트를 반환한다.")
	void findAllByReceiverId_returnEmptyList_whenReceiverNotExist() {
		// given
		UUID receiverId = UUID.randomUUID();

		// when, then
		assertThat(sseEmitterRepository.findAllByReceiverId(receiverId))
			.isEmpty();
	}
}