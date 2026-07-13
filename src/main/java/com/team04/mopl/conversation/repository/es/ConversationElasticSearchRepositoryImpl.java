package com.team04.mopl.conversation.repository.es;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import com.team04.mopl.common.enums.SortDirection;
import com.team04.mopl.conversation.document.ConversationDocument;
import com.team04.mopl.conversation.dto.request.ConversationPageRequest;
import com.team04.mopl.conversation.exception.ConversationErrorCode;
import com.team04.mopl.conversation.exception.ConversationException;

import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.query_dsl.QueryBuilders;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Repository
@RequiredArgsConstructor
public class ConversationElasticSearchRepositoryImpl implements ConversationElasticSearchRepository {

	private final ElasticsearchOperations elasticsearchOperations;

	@Override
	public List<ConversationDocument> searchConversation(ConversationPageRequest request, UUID requestUserId) {
		try {
			// 1. 쿼리 조건 생성
			NativeQuery query = buildSearchQuery(request, requestUserId);

			// 2. 실행 및 결과 추출
			SearchHits<ConversationDocument> searchHits = elasticsearchOperations.search(
				query,
				ConversationDocument.class
			);

			return searchHits.stream()
				.map(hit -> hit.getContent())
				.toList();

		} catch (ElasticsearchException e) {
			log.error("[CONVERSATION_ES] 대화 목록 검색 실패 - requestUserId: {}, Keyword: {}",
				requestUserId, request.keywordLike(), e);

			throw new ConversationException(ConversationErrorCode.CONVERSATION_SEARCH_FAILED, e);
		}
	}

	@Override
	public long countConversation(ConversationPageRequest request, UUID requestUserId) {
		try {
			// 1. 쿼리 조건 생성
			NativeQuery query = NativeQuery.builder()
				.withQuery(createBoolQuery(request, requestUserId))
				.build();

			// 2. 실행 결과 개수 반환
			return elasticsearchOperations.count(query, ConversationDocument.class);

		} catch (ElasticsearchException e) {
			// 예외 발생 시, 0 반환
			log.error("[CONVERSATION_ES] 대화 목록 개수 조회 실패 - requestUserId: {}", requestUserId, e);

			throw new ConversationException(ConversationErrorCode.CONVERSATION_SEARCH_FAILED, e);
		}
	}

	private NativeQuery buildSearchQuery(ConversationPageRequest request, UUID requestUserId) {
		NativeQueryBuilder builder = NativeQuery.builder();

		// 필터링: 쿼리 조건 생성
		builder.withQuery(createBoolQuery(request, requestUserId));

		// 정렬
		builder.withSort(createSort(request));

		// 커서 페이징 구성
		applySearchAfter(builder, request);

		// 개수 제한
		builder.withMaxResults(request.limit() + 1);

		return builder.build();
	}

	// 필터링: 검색 및 필터 조건 생성
	private co.elastic.clients.elasticsearch._types.query_dsl.Query createBoolQuery(
		ConversationPageRequest request,
		UUID requestUserId
	) {
		var boolQuery = QueryBuilders.bool();

		// 1. 완전 일치: 요청자의 대화 참여 여부
		boolQuery.filter(filter -> filter.term(
				term -> term.field("participantIds").value(requestUserId.toString())
			)
		);

		// 2. 부분 일치: 사용자 닉네임 or 다이렉트 메시지 키워드 검색
		if (StringUtils.hasText(request.keywordLike())) {
			boolQuery.must(must -> must.multiMatch(
				multiMatch -> multiMatch.fields(List.of("participantNames", "messageContents"))
					.query(request.keywordLike()))
			);
		}

		return boolQuery.build()._toQuery();
	}

	// 커서 페이지네이션
	private void applySearchAfter(NativeQueryBuilder builder, ConversationPageRequest request) {
		if (StringUtils.hasText(request.cursor()) && request.idAfter() != null) {
			Instant cursorTime = parseCursorToInstant(request.cursor());

			builder.withSearchAfter(List.of(cursorTime.toEpochMilli(), request.idAfter().toString()));
		}
	}

	// 커서 반환
	private Instant parseCursorToInstant(String cursor) {
		try {
			return Instant.parse(cursor);
		} catch (DateTimeParseException e) {
			// 커서 값이 잘못된 형태인 경우, 예외 발생
			throw new ConversationException(ConversationErrorCode.CONVERSATION_INVALID_FORMAT, e)
				.addDetail("invalidCursor", cursor);
		}
	}

	// 정렬
	private Sort createSort(ConversationPageRequest request) {
		Sort.Direction direction = request.sortDirection() == SortDirection.DESCENDING
			? Sort.Direction.DESC
			: Sort.Direction.ASC;

		return Sort.by(direction, "createdAt", "id");
	}
}
