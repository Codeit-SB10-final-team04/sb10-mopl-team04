package com.team04.mopl.conversation.listener;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import java.time.Instant;
import java.util.List;
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

import com.team04.mopl.conversation.event.ConversationCreatedEvent;
import com.team04.mopl.conversation.redis.ConversationRedisStore;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
class ConversationRedisSyncProcessorTest {

	private static final String DLQ_TOPIC = "conversation-sync-dlq";

	@Mock
	private ConversationRedisStore conversationRedisStore;

	@Mock
	private KafkaTemplate<String, Object> kafkaTemplate;

	@Spy
	private MeterRegistry meterRegistry = new SimpleMeterRegistry();

	@InjectMocks
	private ConversationRedisSyncProcessor conversationRedisSyncProcessor;

	/*
	======================================
	   대화 생성 Redis 동기화 로직 테스트
	======================================
	 */
	@Test
	@DisplayName("성공: 참여자가 2명 이상인 대화 생성 이벤트 수신 시 대화와 참여자 목록을 모두 Redis에 저장한다.")
	void syncRedisOnConversationCreated_Success_WithTwoOrMoreParticipants() {
		// given
		UUID conversationId = UUID.randomUUID();
		UUID user1 = UUID.randomUUID();
		UUID user2 = UUID.randomUUID();
		List<UUID> participantIds = List.of(user1, user2);

		ConversationCreatedEvent event = new ConversationCreatedEvent(
			conversationId,
			participantIds,
			Instant.now()
		);

		// when
		conversationRedisSyncProcessor.syncRedisOnConversationCreated(event);

		// then
		verify(conversationRedisStore, times(1)).addConversation(user1, user2, conversationId);
		verify(conversationRedisStore, times(1)).addParticipants(eq(conversationId), anySet());
	}

	@Test
	@DisplayName("성공: 참여자가 2명 미만인 대화 생성 이벤트 수신 시 대화 저장은 생략하고 참여자 목록만 Redis에 저장한다.")
	void syncRedisOnConversationCreated_Success_WithLessThanTwoParticipants() {
		// given
		UUID conversationId = UUID.randomUUID();
		UUID user1 = UUID.randomUUID();
		List<UUID> participantIds = List.of(user1);

		ConversationCreatedEvent event = new ConversationCreatedEvent(
			conversationId,
			participantIds,
			Instant.now()
		);

		// when
		conversationRedisSyncProcessor.syncRedisOnConversationCreated(event);

		// then
		verify(conversationRedisStore, never()).addConversation(any(), any(), any());
		verify(conversationRedisStore, times(1)).addParticipants(eq(conversationId), anySet());
	}

	@Test
	@DisplayName("실패: Redis 추가 중 예외 발생 시, 예외를 밖으로 던져 @Retryable이 작동하게 한다.")
	void syncRedisOnConversationCreated_Fail_ThrowsException() {
		// given
		UUID conversationId = UUID.randomUUID();
		List<UUID> participantIds = List.of(UUID.randomUUID(), UUID.randomUUID());

		ConversationCreatedEvent event = new ConversationCreatedEvent(
			conversationId,
			participantIds,
			Instant.now()
		);

		willThrow(new RuntimeException("Redis Connection Failed"))
			.given(conversationRedisStore).addConversation(any(), any(), any());

		// when & then
		assertThatThrownBy(() -> conversationRedisSyncProcessor.syncRedisOnConversationCreated(event))
			.isInstanceOf(RuntimeException.class)
			.hasMessageContaining("Redis Connection Failed");
	}

	/*
	======================================
	   대화 생성 복구(Recover) 로직 테스트
	======================================
	 */
	@Test
	@DisplayName("성공: 생성 동기화 최종 실패 시(@Recover), Kafka DLQ 토픽으로 이벤트를 정상적으로 발행한다.")
	void recoverCreateFailure_Success() throws Exception {
		// given
		UUID conversationId = UUID.randomUUID();
		ConversationCreatedEvent event = new ConversationCreatedEvent(
			conversationId,
			List.of(UUID.randomUUID(), UUID.randomUUID()),
			Instant.now()
		);

		Exception syncException = new RuntimeException("최종 실패 예외");

		// Kafka 통신 CompletableFuture
		CompletableFuture<SendResult<String, Object>> future = mock(CompletableFuture.class);
		given(kafkaTemplate.send(eq(DLQ_TOPIC), eq(conversationId.toString()), eq(event)))
			.willReturn(future);
		given(future.get(5, TimeUnit.SECONDS)).willReturn(mock(SendResult.class));

		// when
		conversationRedisSyncProcessor.recoverCreateFailure(syncException, event);

		// then
		verify(kafkaTemplate, times(1)).send(DLQ_TOPIC, conversationId.toString(), event);
		verify(future, times(1)).get(5, TimeUnit.SECONDS);
	}

	@Test
	@DisplayName("실패: 생성 동기화 DLQ 발행 중 Kafka 통신 예외가 발생해도, 안전하게 catch 되어 시스템이 중단되지 않는다.")
	void recoverCreateFailure_KafkaFail_SafelyCaught() {
		// given
		UUID conversationId = UUID.randomUUID();
		ConversationCreatedEvent event = new ConversationCreatedEvent(
			conversationId,
			List.of(UUID.randomUUID(), UUID.randomUUID()),
			Instant.now()
		);

		Exception syncException = new RuntimeException("최종 실패 예외");

		willThrow(new RuntimeException("Kafka Broker Down"))
			.given(kafkaTemplate).send(anyString(), anyString(), any());

		// when & then
		assertThatCode(() -> conversationRedisSyncProcessor.recoverCreateFailure(syncException, event))
			.doesNotThrowAnyException();
	}
}
