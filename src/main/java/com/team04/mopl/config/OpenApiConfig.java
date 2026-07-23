package com.team04.mopl.config;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;

@Configuration
public class OpenApiConfig {

	@Bean
	public OpenAPI openAPI() {
		return new OpenAPI()
			.info(new Info()
				.title("모두의 플리 API 문서")
				.description("모두의 플리 프로젝트의 Swagger API 문서입니다.")
				.version("1.0"))
			.tags(List.of(
				new Tag().name("알림").description("알림 API"),
				new Tag().name("사용자 관리").description("사용자 관리 API"),
				new Tag().name("리뷰 관리").description("리뷰 관리 API"),
				new Tag().name("플레이리스트 관리").description("플레이리스트 API"),
				new Tag().name("팔로우 관리").description("팔로우 관리 API"),
				new Tag().name("다이렉트 메시지").description("대화방 및 메시지 관리 API"),
				new Tag().name("콘텐츠 관리").description("콘텐츠 관리 API"),
				new Tag().name("인증 관리").description("인증 API"),
				new Tag().name("시청 세션 관리").description("시청 세션 API"),
				new Tag().name("SSE").description("Server-Sent Events")))
			.servers(List.of(
				new Server().url("http://localhost:8080").description("로컬 서버"),
				new Server().url("https://mopl.kr").description("배포 서버")))
			.components(new Components()
				.addSecuritySchemes("BearerAuth", new SecurityScheme()
					.type(SecurityScheme.Type.HTTP)
					.scheme("bearer")
					.bearerFormat("JWT")
					.description("JWT 액세스 토큰 (로그인 후 발급)"))
				.addSecuritySchemes("CsrfToken", new SecurityScheme()
					.type(SecurityScheme.Type.APIKEY)
					.in(SecurityScheme.In.HEADER)
					.name("X-XSRF-TOKEN")
					.description("CSRF 토큰 (GET /api/auth/csrf-token 호출 후 XSRF-TOKEN 쿠키 값)")))
			.security(List.of(
				new SecurityRequirement()
					.addList("BearerAuth")
					.addList("CsrfToken")));
	}

	@Bean
	public OpenApiCustomizer tagOrderCustomizer() {
		List<String> tagOrder = List.of(
			"알림", "사용자 관리", "리뷰 관리", "플레이리스트 관리",
			"팔로우 관리", "다이렉트 메시지", "콘텐츠 관리", "인증 관리",
			"시청 세션 관리", "SSE"
		);
		Map<String, Integer> orderMap = IntStream.range(0, tagOrder.size())
			.boxed()
			.collect(Collectors.toMap(tagOrder::get, i -> i));

		return openApi -> {
			List<Tag> tags = openApi.getTags();
			if (tags != null) {
				tags.sort(Comparator.comparingInt(
					tag -> orderMap.getOrDefault(tag.getName(), Integer.MAX_VALUE)
				));
			}
		};
	}
}
