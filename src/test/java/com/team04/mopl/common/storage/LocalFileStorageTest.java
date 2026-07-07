package com.team04.mopl.common.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class LocalFileStorageTest {

	@TempDir
	Path tempDir;

	private LocalFileStorage storage() {
		return new LocalFileStorage(tempDir.toString() + "/", 8080);
	}

	@Test
	@DisplayName("파일 저장에 성공하면 directory가 포함된 URL을 반환하고 실제 파일이 생성된다")
	void store_savesFileAndReturnsUrl() throws Exception {
		// given
		MockMultipartFile file = new MockMultipartFile(
			"thumbnail", "thumb.png", "image/png", "image-data".getBytes()
		);

		// when
		String url = storage().store(file, "thumbnails");

		// then
		assertThat(url)
			.startsWith("http://localhost:8080/thumbnails/")
			.endsWith(".png");

		String filename = url.substring(url.lastIndexOf("/") + 1);
		Path savedPath = tempDir.resolve("thumbnails").resolve(filename);
		assertThat(Files.exists(savedPath)).isTrue();
		assertThat(Files.readAllBytes(savedPath)).isEqualTo("image-data".getBytes());
	}

	@Test
	@DisplayName("directory에 따라 저장 경로가 달라진다 (프로필 등 공용 사용)")
	void store_usesDirectoryAsSubPath() {
		// given
		MockMultipartFile file = new MockMultipartFile(
			"image", "profile.jpg", "image/jpeg", "image-data".getBytes()
		);

		// when
		String url = storage().store(file, "profiles");

		// then
		assertThat(url).contains("/profiles/");
		assertThat(Files.exists(tempDir.resolve("profiles"))).isTrue();
	}

	@Test
	@DisplayName("파일 삭제에 성공하면 실제 파일이 제거된다")
	void delete_removesFile() {
		// given
		MockMultipartFile file = new MockMultipartFile(
			"thumbnail", "thumb.png", "image/png", "image-data".getBytes()
		);
		String url = storage().store(file, "thumbnails");

		String filename = url.substring(url.lastIndexOf("/") + 1);
		Path savedPath = tempDir.resolve("thumbnails").resolve(filename);
		assertThat(Files.exists(savedPath)).isTrue();

		// when
		storage().delete(url);

		// then
		assertThat(Files.exists(savedPath)).isFalse();
	}

	@Test
	@DisplayName("존재하지 않는 파일 삭제 요청은 예외 없이 통과한다")
	void delete_passesQuietly_whenFileNotFound() {
		storage().delete("http://localhost:8080/thumbnails/not-exists.png");
	}

	@Test
	@DisplayName("한글/공백 파일명이어도 URL에 원본 파일명이 포함되지 않는다")
	void store_excludesOriginalFilename_fromUrl() {
		// given
		MockMultipartFile file = new MockMultipartFile(
			"thumbnail", "내 사진 파일.png", "image/png", "image-data".getBytes()
		);

		// when
		String url = storage().store(file, "thumbnails");

		// then
		assertThat(url)
			.doesNotContain("내 사진")
			.doesNotContain(" ")
			.endsWith(".png");
	}
}
