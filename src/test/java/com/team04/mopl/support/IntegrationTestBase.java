package com.team04.mopl.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

// Testcontainers 기반 통합 테스트 베이스 클래스
// PostgreSQL + Redis 컨테이너를 static으로 공유하여 테스트 클래스 간 재시작 방지
@SpringBootTest
@Testcontainers
@TestPropertySource(properties = {
	"spring.jpa.hibernate.ddl-auto=none",
	"spring.jpa.properties.hibernate.dialect=com.team04.mopl.config.PostgresTestDialect",
	"spring.sql.init.mode=always",
	"spring.sql.init.schema-locations=classpath:schema-integration-test.sql",
	"spring.batch.job.enabled=false",
	"spring.batch.jdbc.initialize-schema=never",
	"spring.cache.type=simple",
	"mopl.redis.pubsub.enabled=false",
	"notification.realtime.mode=local",
	"spring.kafka.bootstrap-servers=",
	"spring.autoconfigure.exclude="
		+ "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration,"
		+ "org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchClientAutoConfiguration,"
		+ "org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchDataAutoConfiguration,"
		+ "org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchRepositoriesAutoConfiguration,"
		+ "org.opensearch.spring.boot.autoconfigure.OpenSearchRestClientAutoConfiguration,"
		+ "org.opensearch.spring.boot.autoconfigure.OpenSearchRestHighLevelClientAutoConfiguration,"
		+ "org.opensearch.data.client.config.OpenSearchDataAutoConfiguration,"
		+ "org.redisson.spring.starter.RedissonAutoConfigurationV2"
})
@SuppressWarnings("rawtypes")
public abstract class IntegrationTestBase extends ElasticsearchMockingSupport {

	@MockitoBean
	protected KafkaTemplate kafkaTemplate;

	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

	@Container
	static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
		.withExposedPorts(6379);

	@DynamicPropertySource
	static void redisProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.data.redis.host", redis::getHost);
		registry.add("spring.data.redis.port", redis::getFirstMappedPort);
	}
}
