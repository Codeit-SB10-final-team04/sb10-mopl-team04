package com.team04.mopl.conversation.repository.es;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;

import com.team04.mopl.common.enums.SortDirection;
import com.team04.mopl.conversation.document.ConversationDocument;
import com.team04.mopl.conversation.dto.request.ConversationPageRequest;
import com.team04.mopl.conversation.exception.ConversationErrorCode;
import com.team04.mopl.conversation.exception.ConversationException;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;

@ExtendWith(MockitoExtension.class)
class ConversationElasticSearchRepositoryCustomImplTest {

	@Mock
	private ElasticsearchOperations elasticsearchOperations;

	@Captor
	private ArgumentCaptor<NativeQuery> queryCaptor;

	@InjectMocks
	private ConversationElasticSearchRepositoryCustomImpl esRepository;

	@Test
	@DisplayName("성공: 조건에 맞는 대화 목록을 ES에서 검색하여 반환한다. (커서 없음)")
	void searchConversation_FirstPage_Success() {
		// given
		UUID requestUserId = UUID.randomUUID();
		ConversationPageRequest request = new ConversationPageRequest(
			"testKeyword",
			null,
			null,
			10,
			SortDirection.DESCENDING,
			"createdAt"
		);

		ConversationDocument document = mock(ConversationDocument.class);
		SearchHit<ConversationDocument> searchHit = mock(SearchHit.class);
		given(searchHit.getContent()).willReturn(document);

		SearchHits<ConversationDocument> searchHits = mock(SearchHits.class);
		given(searchHits.stream()).willReturn(Stream.of(searchHit));

		given(elasticsearchOperations.search(any(NativeQuery.class), eq(ConversationDocument.class)))
			.willReturn(searchHits);

		// when
		List<ConversationDocument> result = esRepository.searchConversation(request, requestUserId);

		// then
		assertThat(result).hasSize(1);
		assertThat(result.get(0)).isEqualTo(document);
	}

	@Test
	@DisplayName("성공: 유효한 커서(시간)와 idAfter가 포함된 페이징 요청을 정상 처리한다.")
	void searchConversation_WithCursor_Success() {
		// given
		UUID requestUserId = UUID.randomUUID();

		String validCursor = Instant.now().toString();
		UUID idAfter = UUID.randomUUID();

		ConversationPageRequest request = new ConversationPageRequest(
			null,
			validCursor,
			idAfter,
			10,
			SortDirection.DESCENDING,
			"createdAt"
		);

		SearchHits<ConversationDocument> searchHits = mock(SearchHits.class);
		given(searchHits.stream()).willReturn(Stream.empty());

		given(elasticsearchOperations.search(any(NativeQuery.class), eq(ConversationDocument.class)))
			.willReturn(searchHits);

		// when
		esRepository.searchConversation(request, requestUserId);

		// then
		verify(elasticsearchOperations).search(queryCaptor.capture(), eq(ConversationDocument.class));
		NativeQuery capturedQuery = queryCaptor.getValue();

		assertThat(capturedQuery.getSearchAfter()).isNotNull();
		assertThat(capturedQuery.getSearchAfter())
			.containsExactly(Instant.parse(validCursor).toEpochMilli(), idAfter.toString());
	}

	@Test
	@DisplayName("실패: 커서 형식이 올바르지 않으면 CONVERSATION_INVALID_FORMAT 예외가 발생한다.")
	void searchConversation_InvalidCursorFormat_Fail() {
		// given
		UUID requestUserId = UUID.randomUUID();

		String invalidCursor = "invalid-time-format";
		UUID idAfter = UUID.randomUUID();

		ConversationPageRequest request = new ConversationPageRequest(
			null,
			invalidCursor,
			idAfter,
			10,
			SortDirection.DESCENDING,
			"createdAt"
		);

		// when & then
		assertThatThrownBy(() -> esRepository.searchConversation(request, requestUserId))
			.isInstanceOf(ConversationException.class)
			.hasMessage(ConversationErrorCode.CONVERSATION_INVALID_FORMAT.getMessage());
	}

	@Test
	@DisplayName("실패: ES 서버 장애 시 CONVERSATION_SEARCH_FAILED 예외로 변환되어 던져진다.")
	void searchConversation_ElasticsearchException_Fail() {
		// given
		UUID requestUserId = UUID.randomUUID();
		ConversationPageRequest request = new ConversationPageRequest(
			null,
			null,
			null,
			10,
			SortDirection.DESCENDING,
			"createdAt"
		);

		ElasticsearchException esException = mock(ElasticsearchException.class);
		given(elasticsearchOperations.search(any(NativeQuery.class), eq(ConversationDocument.class)))
			.willThrow(esException);

		// when & then
		assertThatThrownBy(() -> esRepository.searchConversation(request, requestUserId))
			.isInstanceOf(ConversationException.class)
			.hasMessage(ConversationErrorCode.CONVERSATION_SEARCH_FAILED.getMessage());
	}

	@Test
	@DisplayName("성공: 조건에 맞는 대화방 전체 개수를 반환한다.")
	void countConversation_Success() {
		// given
		UUID requestUserId = UUID.randomUUID();
		ConversationPageRequest request = new ConversationPageRequest(
			"test",
			null,
			null,
			10,
			SortDirection.DESCENDING,
			"createdAt"
		);

		given(elasticsearchOperations.count(any(NativeQuery.class), eq(ConversationDocument.class)))
			.willReturn(5L);

		// when
		long count = esRepository.countConversation(request, requestUserId);

		// then
		assertThat(count).isEqualTo(5L);
	}

	@Test
	@DisplayName("실패: 개수 조회 시 ES 서버 장애가 발생하면 CONVERSATION_SEARCH_FAILED 예외로 변환된다.")
	void countConversation_ElasticsearchException_Fail() {
		// given
		UUID requestUserId = UUID.randomUUID();
		ConversationPageRequest request = new ConversationPageRequest(
			null,
			null,
			null,
			10,
			SortDirection.DESCENDING,
			"createdAt"
		);

		ElasticsearchException esException = mock(ElasticsearchException.class);
		given(elasticsearchOperations.count(any(NativeQuery.class), eq(ConversationDocument.class)))
			.willThrow(esException);

		// when & then
		assertThatThrownBy(() -> esRepository.countConversation(request, requestUserId))
			.isInstanceOf(ConversationException.class)
			.hasMessage(ConversationErrorCode.CONVERSATION_SEARCH_FAILED.getMessage());
	}

	@Test
	@DisplayName("성공: 검색 조건 및 정렬 방향이 NativeQuery 내부에 올바르게 생성되어 전달된다.")
	void buildSearchQuery_ConditionAndSort_Verified() {
		// given
		UUID requestUserId = UUID.randomUUID();
		String searchKeyword = "testNickname";
		ConversationPageRequest request = new ConversationPageRequest(
			searchKeyword,
			null,
			null,
			10,
			SortDirection.ASCENDING,
			"createdAt"
		);

		SearchHits<ConversationDocument> searchHits = mock(SearchHits.class);
		given(elasticsearchOperations.search(any(NativeQuery.class), eq(ConversationDocument.class)))
			.willReturn(searchHits);

		// when
		esRepository.searchConversation(request, requestUserId);

		// then
		verify(elasticsearchOperations).search(queryCaptor.capture(), eq(ConversationDocument.class));
		NativeQuery capturedQuery = queryCaptor.getValue();

		assertThat(capturedQuery.getMaxResults()).isEqualTo(11);

		assertThat(capturedQuery.getSort().isSorted()).isTrue();
		assertThat(capturedQuery.getSort().getOrderFor("createdAt").getDirection().isAscending()).isTrue();
	}
}