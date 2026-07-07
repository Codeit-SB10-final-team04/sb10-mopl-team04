package com.team04.mopl.common.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

import org.springframework.web.multipart.MultipartFile;

import com.team04.mopl.common.storage.exception.FileStorageException;
import com.team04.mopl.common.utils.AwsProperties;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

// S3 기반 파일 저장소 (배포 환경 전용)
// {directory}/{UUID+확장자} key로 업로드 후 public URL을 반환하고, 삭제 시 URL에서 key를 역추출하여 삭제
// 빈 등록은 S3Config에서 조건부(@ConditionalOnProperty)로 수행 - 이 클래스는 순수 구현체
@Slf4j
@RequiredArgsConstructor
public class S3FileStorage implements FileStorage {

	private final S3Client s3Client;
	private final AwsProperties properties;

	@Override
	public String store(MultipartFile file, String directory) {
		String key = directory + "/" + createFilename(file);

		// AWS SDK는 InputStream을 자동으로 닫지 않으므로 try-with-resources로 닫아 리소스 누수 방지
		try (InputStream inputStream = file.getInputStream()) {
			PutObjectRequest request = PutObjectRequest.builder()
				.bucket(properties.bucket())
				.key(key)
				.contentType(file.getContentType())
				.contentLength(file.getSize())
				.build();

			s3Client.putObject(
				request,
				RequestBody.fromInputStream(inputStream, file.getSize())
			);

			log.info("[S3_STORE] 파일 업로드 완료: key={}", key);

			return toPublicUrl(key);
		} catch (IOException | SdkException e) {
			// SdkException: S3 서버 오류(S3Exception)와 네트워크 등 클라이언트 오류(SdkClientException) 모두 포함
			throw new FileStorageException("파일 저장 실패", e);
		}
	}

	@Override
	public void delete(String fileUrl) {
		String key = extractKey(fileUrl);

		try {
			DeleteObjectRequest request = DeleteObjectRequest.builder()
				.bucket(properties.bucket())
				.key(key)
				.build();

			s3Client.deleteObject(request);

			log.info("[S3_DELETE] 파일 삭제 완료: key={}", key);
		} catch (SdkException e) {
			throw new FileStorageException("파일 삭제 실패", e);
		}
	}

	// UUID + 원본 확장자로 저장 파일명 생성
	// 원본 파일명(한글/공백 등)을 key에 포함하면 public URL이 깨질 수 있어 확장자만 유지
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

	// S3 객체 public URL 생성
	private String toPublicUrl(String key) {
		return "https://%s.s3.%s.amazonaws.com/%s".formatted(
			properties.bucket(),
			properties.region(),
			key
		);
	}

	// URL에서 S3 key 역추출 (도메인 뒤 경로 전체가 key)
	private String extractKey(String fileUrl) {
		if (fileUrl == null || fileUrl.isBlank()) {
			throw new FileStorageException("파일 URL이 비어 있습니다.");
		}

		String domainSuffix = ".amazonaws.com/";
		int index = fileUrl.indexOf(domainSuffix);

		if (index == -1) {
			throw new FileStorageException("S3 URL 형식이 아닙니다: " + fileUrl);
		}

		return fileUrl.substring(index + domainSuffix.length());
	}
}
