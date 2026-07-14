package com.team04.mopl.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.team04.mopl.conversation.repository.es.ConversationElasticSearchRepository;

@TestConfiguration
public class ElasticsearchMockingSupport {

	// ===================================================================
	// [ES Mocking 안내]
	// application-test.yml에서 ES 자동 설정을 제외했기 때문에,
	// 전체 컨텍스트 로드가 필요한 통합 테스트(@SpringBootTest)에서는
	// ES 관련 빈(Bean) 생성 실패(UnsatisfiedDependencyException)가 발생합니다.
	// 이를 방지하기 위해 컨텍스트 로드용 가짜 빈(MockBean)을 주입합니다.
	//
	// TODO: 향후 ES 실제 연동 테스트 필요 시 Testcontainers 환경으로 분리
	// ===================================================================

	@MockitoBean
	protected ConversationElasticSearchRepository conversationElasticSearchRepository;

	@MockitoBean
	protected ElasticsearchOperations elasticsearchOperations;
}
