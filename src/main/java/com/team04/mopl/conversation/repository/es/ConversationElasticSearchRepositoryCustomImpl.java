package com.team04.mopl.conversation.repository.es;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import com.team04.mopl.common.enums.SortDirection;
import com.team04.mopl.conversation.document.ConversationDocument;
import com.team04.mopl.conversation.dto.request.ConversationPageRequest;
import com.team04.mopl.conversation.exception.ConversationErrorCode;
import com.team04.mopl.conversation.exception.ConversationException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Repository
@RequiredArgsConstructor
public class ConversationElasticSearchRepositoryCustomImpl implements ConversationElasticSearchRepositoryCustom {

	private final ElasticsearchOperations elasticsearchOperations;

	@Override
	public List<ConversationDocument> searchConversation(ConversationPageRequest request, UUID requestUserId) {
		try {
			CriteriaQuery query = buildSearchQuery(request, requestUserId);

			SearchHits<ConversationDocument> searchHits = elasticsearchOperations.search(
				query,
				ConversationDocument.class
			);

			return searchHits.stream()
				.map(hit -> hit.getContent())
				.toList();

		} catch (ConversationException e) {
			throw e;
		} catch (Exception e) {
			log.error("[CONVERSATION_ES] 대화 목록 검색 실패 - requestUserId: {}, Keyword: {}",
				requestUserId, request.keywordLike(), e);

			throw new ConversationException(ConversationErrorCode.CONVERSATION_SEARCH_FAILED, e);
		}
	}

	@Override
	public long countConversation(ConversationPageRequest request, UUID requestUserId) {
		try {
			CriteriaQuery query = new CriteriaQuery(createCriteria(request, requestUserId));

			return elasticsearchOperations.count(query, ConversationDocument.class);

		} catch (Exception e) {
			log.error("[CONVERSATION_ES] 대화 목록 개수 조회 실패 - requestUserId: {}", requestUserId, e);

			throw new ConversationException(ConversationErrorCode.CONVERSATION_SEARCH_FAILED, e);
		}
	}

	private CriteriaQuery buildSearchQuery(ConversationPageRequest request, UUID requestUserId) {
		Criteria criteria = createCriteria(request, requestUserId);

		CriteriaQuery query = new CriteriaQuery(criteria);
		query.addSort(createSort(request));
		query.setMaxResults(request.limit() + 1);

		if (StringUtils.hasText(request.cursor()) && request.idAfter() != null) {
			Instant cursorTime = parseCursorToInstant(request.cursor());
			query.setSearchAfter(List.of(cursorTime.toEpochMilli(), request.idAfter().toString()));
		}

		return query;
	}

	private Criteria createCriteria(ConversationPageRequest request, UUID requestUserId) {
		// 1. 완전 일치: 요청자의 대화 참여 여부
		Criteria criteria = Criteria.where("participantIds").is(requestUserId.toString());

		// 2. 부분 일치: 사용자 닉네임 or 다이렉트 메시지 키워드 검색
		if (StringUtils.hasText(request.keywordLike())) {
			Criteria keywordCriteria = new Criteria("participantNames").matches(request.keywordLike())
				.or("messageContents").matches(request.keywordLike());
			criteria = criteria.and(keywordCriteria);
		}

		return criteria;
	}

	private Instant parseCursorToInstant(String cursor) {
		try {
			return Instant.parse(cursor);
		} catch (DateTimeParseException e) {
			throw new ConversationException(ConversationErrorCode.CONVERSATION_INVALID_FORMAT, e)
				.addDetail("invalidCursor", cursor);
		}
	}

	private Sort createSort(ConversationPageRequest request) {
		Sort.Direction direction = request.sortDirection() == SortDirection.DESCENDING
			? Sort.Direction.DESC
			: Sort.Direction.ASC;

		return Sort.by(direction, "createdAt", "id");
	}
}
