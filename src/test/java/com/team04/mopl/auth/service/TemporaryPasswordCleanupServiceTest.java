package com.team04.mopl.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Method;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.team04.mopl.auth.repository.TemporaryPasswordRepository;

class TemporaryPasswordCleanupServiceTest {

	private final TemporaryPasswordRepository temporaryPasswordRepository =
		org.mockito.Mockito.mock(TemporaryPasswordRepository.class);

	private final TemporaryPasswordCleanupService temporaryPasswordCleanupService =
		new TemporaryPasswordCleanupService(temporaryPasswordRepository);

	@Test
	@DisplayName("사용자 id로 임시 비밀번호를 삭제한다")
	void deleteByUserId_deleteTemporaryPasswordByUserId() {
		// given
		UUID userId = UUID.randomUUID();

		// when
		temporaryPasswordCleanupService.deleteByUserId(userId);

		// then
		verify(temporaryPasswordRepository).deleteByUser_Id(userId);
	}

	@Test
	@DisplayName("임시 비밀번호 삭제는 새 트랜잭션으로 실행된다")
	void deleteByUserId_useRequiresNewTransaction() throws NoSuchMethodException {
		// given
		Method method = TemporaryPasswordCleanupService.class.getMethod(
			"deleteByUserId",
			UUID.class
		);

		// when
		Transactional transactional = method.getAnnotation(Transactional.class);

		// then
		assertThat(transactional).isNotNull();
		assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
	}
}