package com.team04.mopl;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.team04.mopl.conversation.repository.es.ConversationElasticSearchRepository;

@SpringBootTest
class MoplApplicationTests {

	@MockitoBean
	private ConversationElasticSearchRepository conversationElasticSearchRepository;

	@MockitoBean
	private ElasticsearchOperations elasticsearchOperations;

	@Test
	void contextLoads() {
	}

}
