package com.team04.mopl.follow.listener;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import com.team04.mopl.follow.event.FollowCreatedEvent;
import com.team04.mopl.follow.event.FollowDeletedEvent;
import com.team04.mopl.follow.redis.FollowRedisStore;

@ExtendWith(MockitoExtension.class)
class FollowRedisSyncProcessorTest {

	private static final String DLQ_TOPIC = "follow-redis-sync-dlq";

	@Mock
	private FollowRedisStore followRedisStore;

	@Mock
	private KafkaTemplate<String, Object> kafkaTemplate;

	@InjectMocks
	private FollowRedisSyncProcessor followRedisSyncProcessor;

	/*
	======================================
	   팔로우 생성 Redis 동기화 로직 테스트
	======================================
	 */
	@Test
	@DisplayName("성공: 팔로우 생성 이벤트 수신 시 Redis에 데이터를 정상적으로 추가한다.")
	void syncRedisOnFollowCreated_Success() {
		// given
		FollowCreatedEvent event = FollowCreatedEvent.of(
			UUID.randomUUID(),
			"팔로위",
			UUID.randomUUID(),
			"팔로워"
		);

		// when
		followRedisSyncProcessor.syncRedisOnFollowCreated(event);

		// then
		verify(followRedisStore, times(1)).addFollow(event.followerId(), event.followeeId());
	}

	@Test
	@DisplayName("실패: Redis 추가 중 예외 발생 시, 예외를 밖으로 던져 @Retryable이 작동하게 한다.")
	void syncRedisOnFollowCreated_Fail_ThrowsException() {
		// given
		FollowCreatedEvent event = FollowCreatedEvent.of(
			UUID.randomUUID(),
			"팔로위",
			UUID.randomUUID(),
			"팔로워"
		);

		willThrow(new RuntimeException("Redis Connection Failed"))
			.given(followRedisStore).addFollow(event.followerId(), event.followeeId());

		// when & then
		assertThatThrownBy(() -> followRedisSyncProcessor.syncRedisOnFollowCreated(event))
			.isInstanceOf(RuntimeException.class)
			.hasMessageContaining("Redis Connection Failed");
	}

	@Test
	@DisplayName("성공: 생성 동기화 최종 실패 시(@Recover), Kafka DLQ 토픽으로 이벤트를 정상적으로 발행한다.")
	void recoverCreateFailure_Success() throws Exception {
		// given
		FollowCreatedEvent event = FollowCreatedEvent.of(
			UUID.randomUUID(),
			"팔로위",
			UUID.randomUUID(),
			"팔로워"
		);

		Exception syncException = new RuntimeException("최종 실패 예외");

		SendResult<String, Object> sendResult = mock(SendResult.class);
		CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(sendResult);

		given(kafkaTemplate.send(eq(DLQ_TOPIC), eq(event.followerId().toString()), eq(event)))
			.willReturn(future);

		// when
		followRedisSyncProcessor.recoverCreateFailure(syncException, event);

		// then
		verify(kafkaTemplate, times(1)).send(DLQ_TOPIC, event.followerId().toString(), event);
	}

	@Test
	@DisplayName("실패: 생성 동기화 DLQ 발행 중 Kafka 통신 예외가 발생해도, 안전하게 catch 되어 시스템이 중단되지 않는다.")
	void recoverCreateFailure_KafkaFail_SafelyCaught() {
		// given
		FollowCreatedEvent event = FollowCreatedEvent.of(
			UUID.randomUUID(),
			"팔로위",
			UUID.randomUUID(),
			"팔로워"
		);

		Exception syncException = new RuntimeException("최종 실패 예외");

		willThrow(new RuntimeException("Kafka Broker Down"))
			.given(kafkaTemplate).send(anyString(), anyString(), any());

		// when & then
		assertThatCode(() -> followRedisSyncProcessor.recoverCreateFailure(syncException, event))
			.doesNotThrowAnyException();
	}

	/*
	======================================
	   팔로우 취소 Redis 동기화 로직 테스트
	======================================
	 */
	@Test
	@DisplayName("성공: 팔로우 취소 이벤트 수신 시 Redis에서 데이터를 정상적으로 삭제한다.")
	void syncRedisOnFollowDeleted_Success() {
		// given
		FollowDeletedEvent event = new FollowDeletedEvent(
			UUID.randomUUID(),
			UUID.randomUUID()
		);

		// when
		followRedisSyncProcessor.syncRedisOnFollowDeleted(event);

		// then
		verify(followRedisStore, times(1)).removeFollow(event.followeeId(), event.followerId());
	}

	@Test
	@DisplayName("실패: Redis 삭제 중 예외 발생 시, 예외를 밖으로 던져 @Retryable이 작동하게 한다.")
	void syncRedisOnFollowDeleted_Fail_ThrowsException() {
		// given
		FollowDeletedEvent event = new FollowDeletedEvent(
			UUID.randomUUID(),
			UUID.randomUUID()
		);

		willThrow(new RuntimeException("Redis Timeout"))
			.given(followRedisStore).removeFollow(event.followeeId(), event.followerId());

		// when & then
		assertThatThrownBy(() -> followRedisSyncProcessor.syncRedisOnFollowDeleted(event))
			.isInstanceOf(RuntimeException.class)
			.hasMessageContaining("Redis Timeout");
	}

	@Test
	@DisplayName("성공: 취소 동기화 최종 실패 시(@Recover), Kafka DLQ 토픽으로 이벤트를 정상적으로 발행한다.")
	void recoverDeleteFailure_Success() {
		// given
		FollowDeletedEvent event = new FollowDeletedEvent(
			UUID.randomUUID(),
			UUID.randomUUID()
		);
		Exception syncException = new RuntimeException("최종 실패 예외");

		SendResult<String, Object> sendResult = mock(SendResult.class);
		CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(sendResult);

		given(kafkaTemplate.send(eq(DLQ_TOPIC), eq(event.followerId().toString()), eq(event)))
			.willReturn(future);

		// when
		followRedisSyncProcessor.recoverDeleteFailure(syncException, event);

		// then
		verify(kafkaTemplate, times(1)).send(DLQ_TOPIC, event.followerId().toString(), event);
	}

	@Test
	@DisplayName("실패: 취소 동기화 DLQ 발행 중 Kafka 통신 예외가 발생해도, 안전하게 catch 되어 시스템이 중단되지 않는다.")
	void recoverDeleteFailure_KafkaFail_SafelyCaught() {
		// given
		FollowDeletedEvent event = new FollowDeletedEvent(
			UUID.randomUUID(),
			UUID.randomUUID()
		);
		Exception syncException = new RuntimeException("최종 실패 예외");

		willThrow(new RuntimeException("Kafka Broker Down"))
			.given(kafkaTemplate).send(anyString(), anyString(), any());

		// when & then
		assertThatCode(() -> followRedisSyncProcessor.recoverDeleteFailure(syncException, event))
			.doesNotThrowAnyException();
	}
}