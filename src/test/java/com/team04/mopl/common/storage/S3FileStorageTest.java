package com.team04.mopl.common.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import com.team04.mopl.common.storage.exception.FileStorageException;
import com.team04.mopl.common.utils.AwsProperties;

import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@ExtendWith(MockitoExtension.class)
class S3FileStorageTest {

	@Mock
	private S3Client s3Client;

	private final AwsProperties properties = new AwsProperties(
		"test-bucket", "ap-northeast-2", "test-access-key", "test-secret-key"
	);

	private S3FileStorage storage() {
		return new S3FileStorage(s3Client, properties);
	}

	@Test
	@DisplayName("파일 업로드에 성공하면 directory가 포함된 S3 public URL을 반환한다")
	void store_returnsPublicUrl_whenUploadSucceeds() {
		// given
		MockMultipartFile file = new MockMultipartFile(
			"thumbnail", "thumb.png", "image/png", "image-data".getBytes()
		);

		// when
		String url = storage().store(file, "thumbnails");

		// then
		assertThat(url)
			.startsWith("https://test-bucket.s3.ap-northeast-2.amazonaws.com/thumbnails/")
			.endsWith(".png");

		ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
		verify(s3Client).putObject(captor.capture(), any(RequestBody.class));
		assertThat(captor.getValue().bucket()).isEqualTo("test-bucket");
		assertThat(captor.getValue().key()).startsWith("thumbnails/");
		assertThat(captor.getValue().contentType()).isEqualTo("image/png");
	}

	@Test
	@DisplayName("directory에 따라 저장 경로가 달라진다 (프로필 등 공용 사용)")
	void store_usesDirectoryAsKeyPrefix() {
		// given
		MockMultipartFile file = new MockMultipartFile(
			"image", "profile.jpg", "image/jpeg", "image-data".getBytes()
		);

		// when
		String url = storage().store(file, "profiles");

		// then
		assertThat(url).contains("/profiles/");
	}

	@Test
	@DisplayName("업로드 중 S3 서버 예외가 발생하면 FileStorageException을 던진다")
	void store_throwsFileStorageException_whenS3Fails() {
		// given
		MockMultipartFile file = new MockMultipartFile(
			"thumbnail", "thumb.png", "image/png", "image-data".getBytes()
		);

		when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
			.thenThrow(S3Exception.builder().message("S3 오류").build());

		// when & then
		assertThatThrownBy(() -> storage().store(file, "thumbnails"))
			.isInstanceOf(FileStorageException.class);
	}

	@Test
	@DisplayName("업로드 중 네트워크 등 클라이언트 예외가 발생해도 FileStorageException을 던진다")
	void store_throwsFileStorageException_whenClientFails() {
		// given
		MockMultipartFile file = new MockMultipartFile(
			"thumbnail", "thumb.png", "image/png", "image-data".getBytes()
		);

		when(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
			.thenThrow(SdkClientException.create("네트워크 오류"));

		// when & then
		assertThatThrownBy(() -> storage().store(file, "thumbnails"))
			.isInstanceOf(FileStorageException.class);
	}

	@Test
	@DisplayName("URL에서 key를 추출해 S3 객체를 삭제한다")
	void delete_deletesObject_withExtractedKey() {
		// given
		String url = "https://test-bucket.s3.ap-northeast-2.amazonaws.com/thumbnails/abc.png";

		// when
		storage().delete(url);

		// then
		ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
		verify(s3Client).deleteObject(captor.capture());
		assertThat(captor.getValue().bucket()).isEqualTo("test-bucket");
		assertThat(captor.getValue().key()).isEqualTo("thumbnails/abc.png");
	}

	@Test
	@DisplayName("S3 URL 형식이 아니면 FileStorageException을 던진다")
	void delete_throwsFileStorageException_whenInvalidUrl() {
		assertThatThrownBy(() -> storage().delete("http://localhost:8080/thumbnails/a.png"))
			.isInstanceOf(FileStorageException.class);
	}

	@Test
	@DisplayName("삭제 중 S3 예외가 발생하면 FileStorageException을 던진다")
	void delete_throwsFileStorageException_whenS3Fails() {
		// given
		String url = "https://test-bucket.s3.ap-northeast-2.amazonaws.com/thumbnails/abc.png";

		when(s3Client.deleteObject(any(DeleteObjectRequest.class)))
			.thenThrow(S3Exception.builder().message("S3 오류").build());

		// when & then
		assertThatThrownBy(() -> storage().delete(url))
			.isInstanceOf(FileStorageException.class);
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

		// then: 원본 파일명 미포함 + 확장자만 유지 → 공백/한글 없는 유효한 URL
		assertThat(url)
			.doesNotContain("내 사진")
			.doesNotContain(" ")
			.endsWith(".png");
	}

	@Test
	@DisplayName("파일명이 없으면 확장자 없이 UUID로만 저장한다")
	void store_usesUuidOnly_whenOriginalFilenameIsNull() {
		// given
		MockMultipartFile file = new MockMultipartFile(
			"thumbnail", null, "image/png", "image-data".getBytes()
		);

		// when
		String url = storage().store(file, "thumbnails");

		// then: key = thumbnails/{UUID} 형태 (확장자 없음)
		String key = url.substring(url.lastIndexOf("thumbnails/") + "thumbnails/".length());
		assertThat(key).doesNotContain(".");
	}
}
