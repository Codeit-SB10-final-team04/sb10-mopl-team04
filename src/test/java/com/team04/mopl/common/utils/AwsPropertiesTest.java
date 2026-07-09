package com.team04.mopl.common.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AwsPropertiesTest {

	@Test
	@DisplayName("bucket이 없으면 예외를 던진다")
	void throwsException_whenBucketMissing() {
		assertThatThrownBy(() -> new AwsProperties(null, "ap-northeast-2", null, null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("bucket");
	}

	@Test
	@DisplayName("region이 없으면 예외를 던진다")
	void throwsException_whenRegionMissing() {
		assertThatThrownBy(() -> new AwsProperties("bucket", " ", null, null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("region");
	}

	@Test
	@DisplayName("access-key만 있고 secret-key가 없으면 예외를 던진다")
	void throwsException_whenOnlyAccessKeyProvided() {
		assertThatThrownBy(() -> new AwsProperties("bucket", "ap-northeast-2", "access", null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("함께 설정");
	}

	@Test
	@DisplayName("secret-key만 있고 access-key가 없으면 예외를 던진다")
	void throwsException_whenOnlySecretKeyProvided() {
		assertThatThrownBy(() -> new AwsProperties("bucket", "ap-northeast-2", "", "secret"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("함께 설정");
	}

	@Test
	@DisplayName("키가 둘 다 없으면 IAM Role 사용으로 간주하고 통과한다")
	void passes_whenNoCredentials() {
		AwsProperties properties = new AwsProperties("bucket", "ap-northeast-2", null, "");

		assertThat(properties.hasStaticCredentials()).isFalse();
	}

	@Test
	@DisplayName("키가 둘 다 있으면 정적 자격증명 사용으로 판단한다")
	void hasStaticCredentials_whenBothKeysProvided() {
		AwsProperties properties = new AwsProperties("bucket", "ap-northeast-2", "access", "secret");

		assertThat(properties.hasStaticCredentials()).isTrue();
	}
}
