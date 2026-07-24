package com.team04.mopl.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

// prod 환경에서 Redisson SSL(rediss://) 연결 설정
// Redisson auto-config는 spring.data.redis.ssl 설정을 무시하므로 직접 빈 생성
@Configuration
@Profile("prod")
public class RedissonConfig {

	@Value("${spring.data.redis.host}")
	private String host;

	@Value("${spring.data.redis.port:6379}")
	private int port;

	@Value("${spring.data.redis.password:}")
	private String password;

	@Bean(destroyMethod = "shutdown")
	public RedissonClient redisson() {
		Config config = new Config();
		config.useSingleServer()
			.setAddress("rediss://" + host + ":" + port)
			.setPassword(password.isEmpty() ? null : password);
		return Redisson.create(config);
	}
}
