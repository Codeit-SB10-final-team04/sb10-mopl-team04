package com.team04.mopl.support;

import org.redisson.api.RedissonClient;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.team04.mopl.conversation.repository.es.ConversationElasticSearchRepository;

// @SpringBootTest에서 외부 인프라(ES, Redisson) 빈을 Mock으로 주입하는 공통 클래스
@TestConfiguration
public class ElasticsearchMockingSupport {

	@MockitoBean
	protected ConversationElasticSearchRepository conversationElasticSearchRepository;

	@MockitoBean
	protected ElasticsearchOperations elasticsearchOperations;

	@MockitoBean
	protected RedissonClient redissonClient;
}
