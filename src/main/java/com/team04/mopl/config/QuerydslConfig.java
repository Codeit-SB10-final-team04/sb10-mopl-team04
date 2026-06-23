package com.team04.mopl.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.querydsl.jpa.impl.JPAQueryFactory;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/*
    Querydsl 설정
    ------------
	JPAQueryFactory를 스프링 빈(Bean)으로 등록하여,
    프로젝트 전역에서 의존성을 주입받아 동적 쿼리를 작성할 수 있도록 설정
 */
@Configuration
public class QuerydslConfig {

	@PersistenceContext
	private EntityManager entityManager;

	@Bean
	public JPAQueryFactory jpaQueryFactory() {
		return new JPAQueryFactory(entityManager);
	}

}