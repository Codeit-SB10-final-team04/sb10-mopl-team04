package com.team04.mopl.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.team04.mopl.common.storage.FileStorage;
import com.team04.mopl.common.storage.S3FileStorage;
import com.team04.mopl.common.utils.AwsProperties;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

// S3 파일 저장소 사용 시(storage.type=s3)에만 활성화되는 설정
@Configuration
@ConditionalOnProperty(name = "storage.type", havingValue = "s3")
@EnableConfigurationProperties(AwsProperties.class)
public class S3Config {

	@Bean
	public S3Client s3Client(AwsProperties properties) {
		return S3Client.builder()
			.region(Region.of(properties.region()))
			.credentialsProvider(resolveCredentialsProvider(properties))
			.build();
	}

	@Bean
	public FileStorage s3FileStorage(S3Client s3Client, AwsProperties properties) {
		return new S3FileStorage(s3Client, properties);
	}

	// 정적 자격증명(access-key/secret-key)이 있으면 사용하고,
	// 없으면 기본 체인(환경변수 → 프로파일 → EC2/ECS IAM Role)을 자동 탐색
	private AwsCredentialsProvider resolveCredentialsProvider(AwsProperties properties) {
		if (properties.hasStaticCredentials()) {
			return StaticCredentialsProvider.create(
				AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())
			);
		}

		return DefaultCredentialsProvider.create();
	}
}
