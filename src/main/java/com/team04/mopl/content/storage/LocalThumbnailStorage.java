package com.team04.mopl.content.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;

@Component
@ConditionalOnProperty(name = "thumbnail.storage.type", havingValue = "local")
public class LocalThumbnailStorage implements ThumbnailStorage {

	private final String uploadDir;

	public LocalThumbnailStorage(@Value("${thumbnail.storage.local.path:thumbnails/}") String uploadDir) {
		this.uploadDir = uploadDir;
	}

	@PostConstruct
	public void init() throws IOException {
		Files.createDirectories(Paths.get(uploadDir));
	}

	// 로컬 용, 배포 시에는 S3ThumbnailStorage를 구현할 예정
	@Override
	public String store(MultipartFile thumbnail) {
		try {
			String filename = UUID.randomUUID() + "_" + thumbnail.getOriginalFilename();
			Path path = Paths.get(uploadDir).resolve(filename);
			Files.copy(thumbnail.getInputStream(), path);
			return "http://localhost:8080/thumbnails/" + filename;
		} catch (IOException e) {
			throw new RuntimeException("썸네일 저장 실패", e);
		}
	}

}
