package com.team04.mopl.conversation.repository.es;

import java.util.UUID;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import com.team04.mopl.conversation.document.ConversationDocument;

public interface ConversationElasticSearchRepository
	extends ElasticsearchRepository<ConversationDocument, UUID>, ConversationElasticSearchRepositoryCustom {
}
