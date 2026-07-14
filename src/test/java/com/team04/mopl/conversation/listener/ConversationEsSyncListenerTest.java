package com.team04.mopl.conversation.listener;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.UpdateQuery;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import com.team04.mopl.directmessage.event.DirectMessageSentEvent;

@ExtendWith(MockitoExtension.class)
class ConversationEsSyncListenerTest {

	@Mock
	private ElasticsearchOperations elasticsearchOperations;

	@Mock
	private KafkaTemplate<String, Object> kafkaTemplate;

	@Captor
	private ArgumentCaptor<UpdateQuery> updateQueryCaptor;

	@InjectMocks
	private ConversationEsSyncListener listener;

	@Test
	@DisplayName("성공: 메시지 이벤트 수신 시 ES에 업데이트 쿼리를 정상적으로 전송한다.")
	void syncMessageToElasticsearch_Success() {
		// given
		UUID conversationId = UUID.randomUUID();
		UUID messageId = UUID.randomUUID();
		DirectMessageSentEvent event = new DirectMessageSentEvent(conversationId, messageId, "테스트 메시지");

		// when
		listener.syncMessageToElasticsearch(event);

		// then
		verify(elasticsearchOperations).update(updateQueryCaptor.capture(), eq(IndexCoordinates.of("conversations")));

		UpdateQuery capturedQuery = updateQueryCaptor.getValue();
		assertThat(capturedQuery.getId()).isEqualTo(conversationId.toString());
		assertThat(capturedQuery.getScript()).contains("ctx._source.messageContents.add(params.newMessage)");
	}

	@Test
	@DisplayName("실패: ES 동기화 중 예외가 발생하면 예외를 다시 던져서 @Retryable이 작동하도록 한다.")
	void syncMessageToElasticsearch_Fail_ThrowsException() {
		// given
		DirectMessageSentEvent event = new DirectMessageSentEvent(UUID.randomUUID(), UUID.randomUUID(), "테스트 메시지");

		willThrow(new RuntimeException("ES Update Failed"))
			.given(elasticsearchOperations).update(any(UpdateQuery.class), any(IndexCoordinates.class));

		// when & then
		assertThatThrownBy(() -> listener.syncMessageToElasticsearch(event))
			.isInstanceOf(RuntimeException.class)
			.hasMessageContaining("ES Update Failed");
	}

	@Test
	@DisplayName("성공: 재시도 마저 최종 실패 시(@Recover), Kafka DLQ 토픽으로 이벤트를 동기적으로 발행한다.")
	void recoverSyncFailure_Success() throws Exception {
		// given
		UUID conversationId = UUID.randomUUID();
		DirectMessageSentEvent event = new DirectMessageSentEvent(
			conversationId,
			UUID.randomUUID(),
			"테스트 메시지"
		);

		Exception syncException = new RuntimeException("최종 실패 예외");

		CompletableFuture<SendResult<String, Object>> future = mock(CompletableFuture.class);
		given(kafkaTemplate.send(eq("conversation-es-sync-dlq"), eq(conversationId.toString()), eq(event)))
			.willReturn(future);

		// when
		listener.recoverSyncFailure(syncException, event);

		// then
		verify(kafkaTemplate).send("conversation-es-sync-dlq", conversationId.toString(), event);
		verify(future).get();
	}

	@Test
	@DisplayName("실패: Kafka DLQ 발행 중 예외가 발생해도, 최후 방어선(try-catch)에 의해 스레드가 종료되지 않는다.")
	void recoverSyncFailure_KafkaFail_HandledSafely() {
		// given
		DirectMessageSentEvent event = new DirectMessageSentEvent(
			UUID.randomUUID(),
			UUID.randomUUID(),
			"테스트 메시지"
		);

		Exception syncException = new RuntimeException("최종 실패 예외");

		// Kafka 전송 자체에서 예외 발생 시뮬레이션
		willThrow(new RuntimeException("Kafka Broker Down"))
			.given(kafkaTemplate).send(anyString(), anyString(), any());

		// when
		assertThatCode(() -> listener.recoverSyncFailure(syncException, event))
			.doesNotThrowAnyException();
	}
}