package com.team04.mopl.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.utility.DockerImageName;

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
	"spring.kafka.consumer.group-id=mopl-notification-integration",
	"spring.kafka.consumer.auto-offset-reset=earliest",
	"spring.autoconfigure.exclude="
		+ "org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchClientAutoConfiguration,"
		+ "org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchDataAutoConfiguration,"
		+ "org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchRepositoriesAutoConfiguration,"
		+ "org.opensearch.spring.boot.autoconfigure.OpenSearchRestClientAutoConfiguration,"
		+ "org.opensearch.spring.boot.autoconfigure.OpenSearchRestHighLevelClientAutoConfiguration,"
		+ "org.opensearch.data.client.config.OpenSearchDataAutoConfiguration,"
		+ "org.redisson.spring.starter.RedissonAutoConfigurationV2"
})
@SuppressWarnings("rawtypes")
public abstract class RealtimeIntegrationTestBase
	extends ElasticsearchMockingSupport {

	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres =
		new PostgreSQLContainer<>("postgres:16-alpine");

	@Container
	@ServiceConnection
	static KafkaContainer kafka =
		new KafkaContainer(
			DockerImageName.parse("apache/kafka-native:3.8.0")
		);

	@Container
	static GenericContainer<?> redis =
		new GenericContainer<>("redis:7-alpine")
			.withExposedPorts(6379);

	@DynamicPropertySource
	static void redisProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.data.redis.host", redis::getHost);
		registry.add("spring.data.redis.port", redis::getFirstMappedPort);
	}
}