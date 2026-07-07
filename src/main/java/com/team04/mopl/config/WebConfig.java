package com.team04.mopl.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@Profile("dev")
public class WebConfig implements WebMvcConfigurer {

	private final String thumbnailUploadDir;
	private final String profileImageUploadDir;

	public WebConfig(
		@Value("${thumbnail.storage.local.path:thumbnails/}") String thumbnailUploadDir,
		@Value("${profile-image.storage.local.path:profile-images/}") String profileImageUploadDir
	) {
		this.thumbnailUploadDir = thumbnailUploadDir;
		this.profileImageUploadDir = profileImageUploadDir;
	}

	@Override
	public void addResourceHandlers(ResourceHandlerRegistry registry) {
		registry.addResourceHandler("/thumbnails/**")
			.addResourceLocations("file:" + thumbnailUploadDir);

		registry.addResourceHandler("/profile-images/**")
			.addResourceLocations("file:" + profileImageUploadDir);
	}
}
