package com.team04.mopl.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// 로컬 파일 저장소(LocalFileStorage) 사용 시 저장된 파일을 정적 리소스로 서빙하는 설정 (개발 환경 전용)
@Configuration
@Profile("dev")
public class WebConfig implements WebMvcConfigurer {

	private final String rootDir;

	public WebConfig(@Value("${storage.local.path:uploads/}") String rootDir) {
		this.rootDir = rootDir;
	}

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		// 썸네일: {root}/thumbnails/ → /thumbnails/**
		registry.addResourceHandler("/thumbnails/**")
			.addResourceLocations("file:" + rootDir + "thumbnails/");
	}
}
