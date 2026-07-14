package com.team04.mopl.conversation.listener;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
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

import com.team04.mopl.conversation.document.ConversationDocument;
import com.team04.mopl.conversation.event.ConversationCreatedEvent;
import com.team04.mopl.conversation.repository.es.ConversationElasticSearchRepository;
import com.team04.mopl.directmessage.event.DirectMessageSentEvent;

@ExtendWith(MockitoExtension.class)
class ConversationEsSyncProcessorTest {

	@Mock
	private ElasticsearchOperations elasticsearchOperations;

	@Mock
	private KafkaTemplate<String, Object> kafkaTemplate;

	@Mock
	private ConversationElasticSearchRepository conversationElasticSearchRepository;

	@Captor
	private ArgumentCaptor<UpdateQuery> updateQueryCaptor;

	@Captor
	private ArgumentCaptor<ConversationDocument> documentCaptor;

	@InjectMocks
	private ConversationEsSyncProcessor processor;

	@Test
	@DisplayName("성공: 대화방 생성 이벤트 수신 시 ES에 초기 문서를 정상적으로 생성한다.")
	void createConversationDocument_Success() {
		// given
		UUID conversationId = UUID.randomUUID();
		ConversationCreatedEvent event = new ConversationCreatedEvent(
			conversationId,
			List.of(UUID.randomUUID()),
			Instant.now()
		);

		given(conversationElasticSearchRepository.findById(conversationId)).willReturn(Optional.empty());

		// when
		processor.createConversationDocument(event);

		// then
		verify(conversationElasticSearchRepository).save(documentCaptor.capture());
		ConversationDocument saved = documentCaptor.getValue();

		assertThat(saved).isNotNull();
		assertThat(saved.getId()).isEqualTo(conversationId);
		assertThat(saved.getParticipantIds()).isEqualTo(event.participantIds());
		assertThat(saved.getMessageContents()).isEmpty();
	}

	@Test
	@DisplayName("실패: 대화방 문서 생성 중 예외가 발생하면 예외를 다시 던져서 @Retryable이 작동하도록 한다.")
	void createConversationDocument_Fail_ThrowsException() {
		// given
		UUID conversationId = UUID.randomUUID();
		ConversationCreatedEvent event = new ConversationCreatedEvent(
			conversationId,
			List.of(UUID.randomUUID()),
			Instant.now()
		);

		given(conversationElasticSearchRepository.findById(conversationId)).willReturn(Optional.empty());

		willThrow(new RuntimeException("ES Save Failed"))
			.given(conversationElasticSearchRepository).save(any(ConversationDocument.class));

		// when & then
		assertThatThrownBy(() -> processor.createConversationDocument(event))
			.isInstanceOf(RuntimeException.class)
			.hasMessageContaining("ES Save Failed");
	}

	@Test
	@DisplayName("성공: 대화방 생성 재시도 마저 최종 실패 시(@Recover), Kafka DLQ 토픽으로 이벤트를 동기적으로 발행한다.")
	void recoverCreateFailure_Success() throws Exception {
		// given
		UUID conversationId = UUID.randomUUID();
		ConversationCreatedEvent event = new ConversationCreatedEvent(
			conversationId,
			List.of(UUID.randomUUID()),
			Instant.now()
		);

		Exception syncException = new RuntimeException("최종 실패 예외");

		SendResult<String, Object> sendResult = mock(SendResult.class);
		CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(sendResult);

		given(kafkaTemplate.send(eq("conversation-es-sync-dlq"), eq(conversationId.toString()), eq(event)))
			.willReturn(future);

		// when
		processor.recoverCreateFailure(syncException, event);

		// then
		verify(kafkaTemplate).send("conversation-es-sync-dlq", conversationId.toString(), event);
	}

	@Test
	@DisplayName("실패: 대화방 생성 DLQ 발행 중 예외가 발생해도 스레드가 종료되지 않는다.")
	void recoverCreateFailure_KafkaFail_HandledSafely() {
		// given
		UUID conversationId = UUID.randomUUID();
		ConversationCreatedEvent event = new ConversationCreatedEvent(
			conversationId,
			List.of(UUID.randomUUID()),
			Instant.now()
		);

		Exception syncException = new RuntimeException("최종 실패 예외");

		willThrow(new RuntimeException("Kafka Broker Down"))
			.given(kafkaTemplate).send(anyString(), anyString(), any());

		// when & then
		assertThatCode(() -> processor.recoverCreateFailure(syncException, event))
			.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("성공: 메시지 이벤트 수신 시 ES에 업데이트 쿼리를 정상적으로 전송한다.")
	void syncMessageToElasticsearch_Success() {
		// given
		UUID conversationId = UUID.randomUUID();
		UUID messageId = UUID.randomUUID();
		DirectMessageSentEvent event = new DirectMessageSentEvent(
			conversationId,
			messageId,
			"테스트 메시지"
		);

		given(elasticsearchOperations.getIndexCoordinatesFor(ConversationDocument.class))
			.willReturn(IndexCoordinates.of("conversations"));

		// when
		processor.syncMessageToElasticsearch(event);

		// then
		verify(elasticsearchOperations).update(updateQueryCaptor.capture(), eq(IndexCoordinates.of("conversations")));

		UpdateQuery capturedQuery = updateQueryCaptor.getValue();
		assertThat(capturedQuery.getId()).isEqualTo(conversationId.toString());
		assertThat(capturedQuery.getScript()).contains("ctx._source.messageContents.add(params.newMessage)");
		assertThat(capturedQuery.getScriptedUpsert()).isTrue();
	}

	@Test
	@DisplayName("실패: ES 동기화 중 예외가 발생하면 예외를 다시 던져서 @Retryable이 작동하도록 한다.")
	void syncMessageToElasticsearch_Fail_ThrowsException() {
		// given
		DirectMessageSentEvent event = new DirectMessageSentEvent(UUID.randomUUID(), UUID.randomUUID(), "테스트 메시지");

		given(elasticsearchOperations.getIndexCoordinatesFor(ConversationDocument.class))
			.willReturn(IndexCoordinates.of("conversations"));

		willThrow(new RuntimeException("ES Update Failed"))
			.given(elasticsearchOperations).update(any(UpdateQuery.class), any(IndexCoordinates.class));

		// when & then
		assertThatThrownBy(() -> processor.syncMessageToElasticsearch(event))
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

		SendResult<String, Object> sendResult = mock(SendResult.class);
		CompletableFuture<SendResult<String, Object>> future = CompletableFuture.completedFuture(sendResult);

		given(kafkaTemplate.send(eq("conversation-es-sync-dlq"), eq(conversationId.toString()), eq(event)))
			.willReturn(future);

		// when
		processor.recoverSyncFailure(syncException, event);

		// then
		verify(kafkaTemplate).send("conversation-es-sync-dlq", conversationId.toString(), event);
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

		willThrow(new RuntimeException("Kafka Broker Down"))
			.given(kafkaTemplate).send(anyString(), anyString(), any());

		// when & then
		assertThatCode(() -> processor.recoverSyncFailure(syncException, event))
			.doesNotThrowAnyException();
	}
}