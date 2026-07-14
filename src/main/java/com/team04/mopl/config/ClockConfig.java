package com.team04.mopl.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/*
    Cache 설정
    -----------
    시간에 의존하는 비즈니스 로직의
    유연한 단위 테스트를 위해 Clock 객체를 스프링 빈으로 등록
 */
@Configuration
public class ClockConfig {

	@Bean
	public Clock clock() {
		return Clock.systemDefaultZone();
	}
}
