package com.team04.mopl.config;

import java.time.Duration;

import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/*
    Cache 설정
    -----------
    캐시 활성화를 위한 설정 파일
    - Caffeine: application-dev.yml (spring.cache.type=caffeine)
    - Redis: spring.cache.type=redis일 때 아래 Bean으로 캐시별 TTL, JSON 직렬화 설정
 */
@Configuration
@EnableCaching
public class CacheConfig {

	@Bean
	@ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis")
	public RedisCacheManagerBuilderCustomizer contentListCacheCustomizer() {
		return builder -> builder
			.withCacheConfiguration("contentList",
				RedisCacheConfiguration.defaultCacheConfig()
					.entryTtl(Duration.ofSeconds(30))
					.serializeKeysWith(
						RedisSerializationContext.SerializationPair
							.fromSerializer(new StringRedisSerializer()))
					.serializeValuesWith(
						RedisSerializationContext.SerializationPair
							.fromSerializer(new GenericJackson2JsonRedisSerializer()))
			);
	}
}
