package com.team04.mopl.common.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.team04.mopl.common.storage.exception.FileStorageException;

// 로컬 파일 시스템 기반 저장소 (개발 환경 전용, storage.type=local)
// {root}/{directory}/{filename} 경로에 저장하고 http://localhost:8080/{directory}/{filename} URL 반환
@Component
@ConditionalOnProperty(name = "storage.type", havingValue = "local")
public class LocalFileStorage implements FileStorage {

	private final String rootDir;
	private final int serverPort;

	public LocalFileStorage(
		@Value("${storage.local.path:uploads/}") String rootDir,
		@Value("${server.port:8080}") int serverPort
	) {
		this.rootDir = rootDir;
		this.serverPort = serverPort;
	}

	@Override
	public String store(MultipartFile file, String directory) {
		try {
			String filename = createFilename(file);

			// directory에 "../" 등이 들어와도 루트 밖으로 벗어나지 못하도록 검증
			Path root = Paths.get(rootDir).toAbsolutePath().normalize();
			Path directoryRoot = root.resolve(directory).normalize();
			if (!directoryRoot.startsWith(root)) {
				throw new FileStorageException("허용되지 않은 저장 경로입니다: " + directory);
			}

			Files.createDirectories(directoryRoot);

			Path path = directoryRoot.resolve(filename).normalize();
			if (!path.startsWith(directoryRoot)) {
				throw new FileStorageException("허용되지 않은 파일 경로입니다.");
			}

			try (var inputStream = file.getInputStream()) {
				Files.copy(inputStream, path);
			}

			return "http://localhost:" + serverPort + "/" + directory + "/" + filename;
		} catch (IOException e) {
			throw new FileStorageException("파일 저장 실패", e);
		}
	}

	@Override
	public void delete(String fileUrl) {
		if (fileUrl == null || fileUrl.isBlank()) {
			throw new FileStorageException("파일 URL이 비어 있습니다.");
		}

		try {
			// URL 형식: http://localhost:8080/{directory}/{filename}
			String[] segments = fileUrl.split("/");

			if (segments.length < 2) {
				throw new FileStorageException("파일 URL 형식이 아닙니다: " + fileUrl);
			}

			String filename = segments[segments.length - 1];
			String directory = segments[segments.length - 2];

			// 조작된 URL(../ 포함 등)로 루트 밖 파일을 삭제하지 못하도록 검증
			Path root = Paths.get(rootDir).toAbsolutePath().normalize();
			Path path = root.resolve(directory).resolve(filename).normalize();
			if (!path.startsWith(root)) {
				throw new FileStorageException("허용되지 않은 파일 경로입니다: " + fileUrl);
			}

			Files.deleteIfExists(path);
		} catch (IOException e) {
			throw new FileStorageException("파일 삭제 실패", e);
		}
	}

	// UUID + 원본 확장자로 저장 파일명 생성 (원본 파일명은 URL 안전성을 위해 미포함)
	private String createFilename(MultipartFile file) {
		return UUID.randomUUID() + extractExtension(file.getOriginalFilename());
	}

	// 원본 파일명에서 확장자 추출 (없으면 빈 문자열)
	private String extractExtension(String originalFilename) {
		if (originalFilename == null) {
			return "";
		}

		int dotIndex = originalFilename.lastIndexOf('.');

		// 확장자가 없거나(-1) 숨김 파일(.png)이거나 점으로 끝나는 경우 제외
		if (dotIndex <= 0 || dotIndex == originalFilename.length() - 1) {
			return "";
		}

		return originalFilename.substring(dotIndex);
	}
}
