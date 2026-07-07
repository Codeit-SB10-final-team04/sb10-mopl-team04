package com.team04.mopl.common.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import com.team04.mopl.common.storage.exception.FileStorageException;

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

	@Test
	@DisplayName("확장자가 없는 파일명은 URL에 확장자가 붙지 않는다")
	void store_handlesFilenameWithoutExtension() {
		// given
		MockMultipartFile file = new MockMultipartFile(
			"thumbnail", "thumb", "image/png", "image-data".getBytes()
		);

		// when
		String url = storage().store(file, "thumbnails");

		// then: URL 마지막 조각 = UUID만 (확장자 없음)
		String filename = url.substring(url.lastIndexOf("/") + 1);
		assertThat(filename).doesNotContain(".");
	}

	@Test
	@DisplayName("숨김 파일 형식(.png)의 파일명은 확장자로 취급하지 않는다")
	void store_handlesHiddenFileStyleName() {
		// given
		MockMultipartFile file = new MockMultipartFile(
			"thumbnail", ".png", "image/png", "image-data".getBytes()
		);

		// when
		String url = storage().store(file, "thumbnails");

		// then
		String filename = url.substring(url.lastIndexOf("/") + 1);
		assertThat(filename).doesNotContain(".");
	}

	@Test
	@DisplayName("directory에 경로 탈출(../)이 포함되면 FileStorageException을 던진다")
	void store_throwsException_whenDirectoryEscapesRoot() {
		// given
		MockMultipartFile file = new MockMultipartFile(
			"thumbnail", "thumb.png", "image/png", "image-data".getBytes()
		);

		// when & then
		assertThatThrownBy(() -> storage().store(file, "../escape"))
			.isInstanceOf(FileStorageException.class);
	}

	@Test
	@DisplayName("루트 밖을 가리키는 조작된 URL 삭제 요청은 FileStorageException을 던진다")
	void delete_throwsException_whenUrlEscapesRoot() {
		assertThatThrownBy(
				() -> storage().delete("http://localhost:8080/../secret.txt"))
			.isInstanceOf(FileStorageException.class);
	}

	@Test
	@DisplayName("URL이 null이면 FileStorageException을 던진다")
	void delete_throwsException_whenUrlIsNull() {
		assertThatThrownBy(() -> storage().delete(null))
			.isInstanceOf(FileStorageException.class);
	}
}
