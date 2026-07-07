package com.team04.mopl.user.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.team04.mopl.user.storage.exception.ProfileImageStorageException;

import jakarta.annotation.PostConstruct;

@Component
@ConditionalOnProperty(name = "profile-image.storage.type", havingValue = "local")
public class LocalProfileImageStorage implements ProfileImageStorage {

	private static final String PROFILE_IMAGE_URL_PREFIX = "http://localhost:8080/profile-images/";

	private final String uploadDir;

	public LocalProfileImageStorage(@Value("${profile-image.storage.local.path:profile-images/}") String uploadDir) {
		this.uploadDir = uploadDir;
	}

	@PostConstruct
	public void init() throws IOException {
		Files.createDirectories(Paths.get(uploadDir));
	}

	// 로컬 프로필 이미지 저장
	@Override
	public String store(MultipartFile profileImage) {
		try {
			String originalFilename = Paths.get(
				profileImage.getOriginalFilename() == null ? "profile-image" : profileImage.getOriginalFilename()
			).getFileName().toString();
			String filename = UUID.randomUUID() + "_" + originalFilename;
			Path uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
			Path path = uploadRoot.resolve(filename).normalize();

			if (!path.startsWith(uploadRoot)) {
				throw new ProfileImageStorageException("허용되지 않은 파일 경로입니다.");
			}

			try (var inputStream = profileImage.getInputStream()) {
				Files.copy(inputStream, path);
			}

			return PROFILE_IMAGE_URL_PREFIX + filename;
		} catch (IOException exception) {
			throw new ProfileImageStorageException("프로필 이미지 저장 실패", exception);
		}
	}

	// 로컬 프로필 이미지 삭제
	@Override
	public void delete(String profileImageUrl) {
		if (profileImageUrl == null || profileImageUrl.isBlank()) {
			return;
		}

		try {
			String filename = profileImageUrl.substring(profileImageUrl.lastIndexOf("/") + 1);
			Path path = Paths.get(uploadDir).resolve(filename);

			Files.deleteIfExists(path);
		} catch (IOException exception) {
			throw new ProfileImageStorageException("프로필 이미지 삭제 실패", exception);
		}
	}
}
