package com.team04.mopl.common.utils;

import org.springframework.boot.context.properties.ConfigurationProperties;

// storage.s3 설정값을 바인딩하는 AWS 설정 클래스
// access-key/secret-key는 선택값 - 없으면 DefaultCredentialsProvider(환경변수, 프로파일, IAM Role)로 폴백
@ConfigurationProperties(prefix = "storage.s3")
public record AwsProperties(
	String bucket,
	String region,
	String accessKey,
	String secretKey
) {

	public AwsProperties {
		if (bucket == null || bucket.isBlank()) {
			throw new IllegalArgumentException("S3 bucket은 필수입니다.");
		}

		if (region == null || region.isBlank()) {
			throw new IllegalArgumentException("S3 region은 필수입니다.");
		}

		// 키는 둘 다 없거나(IAM Role 사용) 둘 다 있어야 함 - 한쪽만 있으면 설정 실수
		if (hasText(accessKey) != hasText(secretKey)) {
			throw new IllegalArgumentException("S3 access-key와 secret-key는 함께 설정되어야 합니다.");
		}
	}

	// 정적 자격증명(access-key/secret-key)이 설정되어 있는지 확인
	public boolean hasStaticCredentials() {
		return hasText(accessKey) && hasText(secretKey);
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}
}
