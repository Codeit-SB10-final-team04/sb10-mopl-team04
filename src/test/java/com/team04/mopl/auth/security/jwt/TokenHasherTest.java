package com.team04.mopl.auth.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TokenHasherTest {

	private final TokenHasher tokenHasher = new TokenHasher();

	@Test
	@DisplayName("같은 토큰은 같은 해시값으로 변환한다")
	void hash_returnsSameHash_whenTokenIsSame() {
		// given
		String token = "refresh-token";

		// when
		String firstHash = tokenHasher.hash(token);
		String secondHash = tokenHasher.hash(token);

		// then
		assertThat(firstHash).isEqualTo(secondHash);
	}

	@Test
	@DisplayName("다른 토큰은 다른 해시값으로 변환한다")
	void hash_returnsDifferentHash_whenTokenIsDifferent() {
		// given
		String firstToken = "refresh-token-1";
		String secondToken = "refresh-token-2";

		// when
		String firstHash = tokenHasher.hash(firstToken);
		String secondHash = tokenHasher.hash(secondToken);

		// then
		assertThat(firstHash).isNotEqualTo(secondHash);
	}

	@Test
	@DisplayName("토큰 원문과 다른 해시값을 반환한다")
	void hash_returnsEncodedHash_whenTokenProvided() {
		// given
		String token = "refresh-token";

		// when
		String hash = tokenHasher.hash(token);

		// then
		assertThat(hash).isNotBlank();
		assertThat(hash).isNotEqualTo(token);
	}
}