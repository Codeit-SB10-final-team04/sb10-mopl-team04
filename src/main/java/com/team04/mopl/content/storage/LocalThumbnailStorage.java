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

import com.team04.mopl.content.storage.exception.FileStorageException;

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
			String originalFilename = Paths.get(
				thumbnail.getOriginalFilename() == null ? "thumbnail" : thumbnail.getOriginalFilename()
			).getFileName().toString();
			String filename = UUID.randomUUID() + "_" + originalFilename;
			Path uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
			Path path = uploadRoot.resolve(filename).normalize();
			if (!path.startsWith(uploadRoot)) {
				throw new FileStorageException("허용되지 않은 파일 경로입니다.");
			}
			try (var inputStream = thumbnail.getInputStream()) {
				Files.copy(inputStream, path);
			}
			return "http://localhost:8080/thumbnails/" + filename;
		} catch (IOException e) {
			throw new FileStorageException("썸네일 저장 실패", e);
		}
	}

	@Override
	public void delete(String thumbnailUrl) {
		try {
			String filename = thumbnailUrl.substring(thumbnailUrl.lastIndexOf("/") + 1);
			Path path = Paths.get(uploadDir).resolve(filename);
			Files.deleteIfExists(path);
		} catch (IOException e) {
			throw new FileStorageException("썸네일 삭제 실패", e);
		}
	}

}
