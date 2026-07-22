package com.team04.mopl.directmessage.listener;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import com.team04.mopl.directmessage.dto.response.DirectMessageDto;
import com.team04.mopl.directmessage.event.DirectMessageCreatedEvent;
import com.team04.mopl.directmessage.event.DirectMessageReadEvent;
import com.team04.mopl.directmessage.redis.DirectMessageRedisStore;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
class DirectMessageRedisSyncProcessorTest {

	private static final String DLQ_TOPIC = "direct-message-sync-dlq";

	@Mock
	private DirectMessageRedisStore directMessageRedisStore;

	@Mock
	private KafkaTemplate<String, Object> kafkaTemplate;

	@Spy
	private MeterRegistry meterRegistry = new SimpleMeterRegistry();

	@InjectMocks
	private DirectMessageRedisSyncProcessor directMessageRedisSyncProcessor;

	/*
	======================================
	   DM 생성 Redis 동기화 로직 테스트
	======================================
	 */
	@Test
	@DisplayName("성공: DM 생성 이벤트 수신 시 Redis에 데이터를 추가하고 수신자 안읽음 카운트를 증가시킨다.")
	void syncRedisOnDirectMessageCreated_Success() {
		// given
		UUID conversationId = UUID.randomUUID();
		UUID receiverId = UUID.randomUUID();
		DirectMessageDto mockDto = mock(DirectMessageDto.class);

		DirectMessageCreatedEvent event = DirectMessageCreatedEvent.of(
			UUID.randomUUID(),
			receiverId,
			mockDto
		);

		// when
		directMessageRedisSyncProcessor.syncRedisOnDirectMessageCreated(event);

		// then
		verify(directMessageRedisStore, times(1)).addDirectMessage(any(), any(), any());
	}

	@Test
	@DisplayName("실패: Redis 추가 중 예외 발생 시, 예외를 밖으로 던져 @Retryable이 작동하게 한다.")
	void syncRedisOnDirectMessageCreated_Fail_ThrowsException() {
		// given
		UUID conversationId = UUID.randomUUID();
		UUID receiverId = UUID.randomUUID();
		DirectMessageDto mockDto = mock(DirectMessageDto.class);
		given(mockDto.conversationId()).willReturn(conversationId);

		DirectMessageCreatedEvent event = DirectMessageCreatedEvent.of(
			UUID.randomUUID(),
			receiverId,
			mockDto
		);

		willThrow(new RuntimeException("Redis Connection Failed"))
			.given(directMessageRedisStore)
			.addDirectMessage(any(UUID.class), any(UUID.class), any(DirectMessageDto.class));

		// when & then
		assertThatThrownBy(() -> directMessageRedisSyncProcessor.syncRedisOnDirectMessageCreated(event))
			.isInstanceOf(RuntimeException.class)
			.hasMessageContaining("Redis Connection Failed");
	}

	@Test
	@DisplayName("성공: 생성 동기화 최종 실패 시(@Recover), Kafka DLQ 토픽으로 이벤트를 정상적으로 발행하고 메트릭을 기록한다.")
	void recoverCreateFailure_Success() throws Exception {
		// given
		UUID directMessageId = UUID.randomUUID();
		DirectMessageDto mockDto = mock(DirectMessageDto.class);
		DirectMessageCreatedEvent event = DirectMessageCreatedEvent.of(
			UUID.randomUUID(),
			directMessageId,
			mockDto
		);

		Exception syncException = new RuntimeException("최종 실패 예외");

		// Kafka 통신 CompletableFuture 모킹
		CompletableFuture<SendResult<String, Object>> future = mock(CompletableFuture.class);
		given(kafkaTemplate.send(eq(DLQ_TOPIC), eq(directMessageId.toString()), eq(event)))
			.willReturn(future);
		given(future.get(5, TimeUnit.SECONDS)).willReturn(mock(SendResult.class));

		// when
		directMessageRedisSyncProcessor.recoverCreateFailure(syncException, event);

		// then
		verify(kafkaTemplate, times(1)).send(DLQ_TOPIC, directMessageId.toString(), event);
		verify(future, times(1)).get(5, TimeUnit.SECONDS);

		// 메트릭 검증
		double count = meterRegistry.get("mopl.dm.redis.sync.dlq.publish")
			.tag("operation", "create")
			.tag("result", "success")
			.counter()
			.count();
		assertThat(count).isEqualTo(1.0);
	}

	@Test
	@DisplayName("실패: 생성 동기화 DLQ 발행 중 Kafka 통신 예외 발생 시, 예외를 다시 던져 오프셋 커밋을 방지한다.")
	void recoverCreateFailure_KafkaFail_ThrowsException() {
		// given
		UUID directMessageId = UUID.randomUUID();
		DirectMessageDto mockDto = mock(DirectMessageDto.class);
		DirectMessageCreatedEvent event = DirectMessageCreatedEvent.of(
			UUID.randomUUID(),
			directMessageId,
			mockDto
		);

		Exception syncException = new RuntimeException("최종 실패 예외");

		willThrow(new RuntimeException("Kafka Broker Down"))
			.given(kafkaTemplate).send(anyString(), anyString(), any());

		// when & then
		assertThatThrownBy(() -> directMessageRedisSyncProcessor.recoverCreateFailure(syncException, event))
			.isInstanceOf(RuntimeException.class)
			.hasMessageContaining("DLQ 발행 실패로 인한 이벤트 유실 방지");

		// 메트릭 검증
		double count = meterRegistry.get("mopl.dm.redis.sync.dlq.publish")
			.tag("operation", "create")
			.tag("result", "failure")
			.counter()
			.count();
		assertThat(count).isEqualTo(1.0);
	}

	/*
	======================================
	   DM 읽음 처리 Redis 동기화 로직 테스트
	======================================
	 */
	@Test
	@DisplayName("성공: DM 읽음 처리 이벤트 수신 시 Redis에서 안 읽음 개수를 정상적으로 감소시킨다.")
	void syncRedisOnDirectMessageRead_Success() {
		// given
		UUID receiverId = UUID.randomUUID();
		UUID conversationId = UUID.randomUUID();
		DirectMessageReadEvent event = new DirectMessageReadEvent(
			receiverId,
			conversationId,
			UUID.randomUUID()
		);

		// when
		directMessageRedisSyncProcessor.syncRedisOnDirectMessageRead(event);

		// then
		verify(directMessageRedisStore, times(1)).decrementUnreadCount(receiverId, conversationId);
	}

	@Test
	@DisplayName("실패: Redis 읽음 개수 감소 중 예외 발생 시, 예외를 밖으로 던져 @Retryable이 작동하게 한다.")
	void syncRedisOnDirectMessageRead_Fail_ThrowsException() {
		// given
		UUID receiverId = UUID.randomUUID();
		UUID conversationId = UUID.randomUUID();
		DirectMessageReadEvent event = new DirectMessageReadEvent(
			receiverId,
			conversationId,
			UUID.randomUUID()
		);

		willThrow(new RuntimeException("Redis Timeout"))
			.given(directMessageRedisStore).decrementUnreadCount(receiverId, conversationId);

		// when & then
		assertThatThrownBy(() -> directMessageRedisSyncProcessor.syncRedisOnDirectMessageRead(event))
			.isInstanceOf(RuntimeException.class)
			.hasMessageContaining("Redis Timeout");
	}

	@Test
	@DisplayName("성공: 읽음 동기화 최종 실패 시(@Recover), DLQ 토픽으로 이벤트를 정상적으로 발행하고 메트릭을 기록한다.")
	void recoverReadFailure_Success() throws Exception {
		// given
		DirectMessageReadEvent event = new DirectMessageReadEvent(
			UUID.randomUUID(),
			UUID.randomUUID(),
			UUID.randomUUID()
		);
		Exception syncException = new RuntimeException("최종 실패 예외");

		// Kafka 통신 CompletableFuture 모킹
		CompletableFuture<SendResult<String, Object>> future = mock(CompletableFuture.class);
		given(kafkaTemplate.send(eq(DLQ_TOPIC), eq(event.directMessageId().toString()), eq(event)))
			.willReturn(future);
		given(future.get(5, TimeUnit.SECONDS)).willReturn(mock(SendResult.class));

		// when & then
		assertThatCode(() -> directMessageRedisSyncProcessor.recoverReadFailure(syncException, event))
			.doesNotThrowAnyException();

		// 읽음 실패 시 DLQ 토픽으로 이벤트를 정상적으로 발행함을 검증
		verify(kafkaTemplate, times(1)).send(DLQ_TOPIC, event.directMessageId().toString(), event);
		verify(future, times(1)).get(5, TimeUnit.SECONDS);

		// 메트릭 검증
		double count = meterRegistry.get("mopl.dm.redis.sync.dlq.publish")
			.tag("operation", "read")
			.tag("result", "success")
			.counter()
			.count();
		assertThat(count).isEqualTo(1.0);
	}

	@Test
	@DisplayName("실패: 취소 동기화 DLQ 발행 중 Kafka 통신 예외 발생 시, 예외를 다시 던져 오프셋 커밋을 방지한다.")
	void recoverReadFailure_KafkaFail_ThrowsException() {
		// given
		DirectMessageReadEvent event = new DirectMessageReadEvent(
			UUID.randomUUID(),
			UUID.randomUUID(),
			UUID.randomUUID()
		);
		Exception syncException = new RuntimeException("최종 실패 예외");

		willThrow(new RuntimeException("Kafka Broker Down"))
			.given(kafkaTemplate).send(anyString(), anyString(), any());

		// when & then
		assertThatThrownBy(() -> directMessageRedisSyncProcessor.recoverReadFailure(syncException, event))
			.isInstanceOf(RuntimeException.class)
			.hasMessageContaining("DLQ 발행 실패로 인한 이벤트 유실 방지");

		// 메트릭 검증
		double count = meterRegistry.get("mopl.dm.redis.sync.dlq.publish")
			.tag("operation", "read")
			.tag("result", "failure")
			.counter()
			.count();
		assertThat(count).isEqualTo(1.0);
	}
}
