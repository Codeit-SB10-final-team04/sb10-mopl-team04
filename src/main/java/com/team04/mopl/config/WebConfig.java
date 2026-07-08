package com.team04.mopl.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// 로컬 파일 저장소 사용 시 저장된 파일을 정적 리소스로 서빙하는 개발 환경 전용 설정
@Configuration
@Profile("dev")
public class WebConfig implements WebMvcConfigurer {

	private final String rootDir;
	private final String profileImageUploadDir;

	public WebConfig(
		@Value("${storage.local.path:uploads/}") String rootDir,
		@Value("${profile-image.storage.local.path:profile-images/}") String profileImageUploadDir
	) {
		// 문자열 결합 시 경로가 붙는 문제 방지를 위한 trailing slash 보정
		this.rootDir = appendTrailingSlash(rootDir);
		this.profileImageUploadDir = appendTrailingSlash(profileImageUploadDir);
	}

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		// 썸네일 정적 리소스 매핑
		registry.addResourceHandler("/thumbnails/**")
			.addResourceLocations("file:" + rootDir + "thumbnails/");

		// 프로필 이미지 정적 리소스 매핑
		registry.addResourceHandler("/profile-images/**")
			.addResourceLocations("file:" + profileImageUploadDir);
	}

	private static String appendTrailingSlash(String path) {
		return path.endsWith("/") ? path : path + "/";
	}
}
