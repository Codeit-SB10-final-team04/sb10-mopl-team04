package com.team04.mopl.content.repository;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import com.team04.mopl.config.JpaAuditingConfig;
import com.team04.mopl.config.QuerydslConfig;
import com.team04.mopl.content.entity.Content;
import com.team04.mopl.content.entity.ContentType;

@DataJpaTest
@Import({JpaAuditingConfig.class, QuerydslConfig.class})
@ActiveProfiles("test")
class ContentRepositoryTest {

	@Autowired
	private ContentRepository contentRepository;

	@Test
	@DisplayName("동일한 title과 type의 Content가 존재하면 true를 반환한다")
	void existsByTitleAndType_returnsTrue_whenSameTitleAndTypeExist() {
		// given
		contentRepository.save(Content.builder()
			.title("Arsenal vs Chelsea")
			.type(ContentType.sport)
			.description("FA Cup Final")
			.thumbnailUrl("")
			.build());

		// when
		boolean result = contentRepository.existsByTitleAndType("Arsenal vs Chelsea", ContentType.sport);

		// then
		assertThat(result).isTrue();
	}

	@Test
	@DisplayName("title이 같아도 type이 다르면 false를 반환한다")
	void existsByTitleAndType_returnsFalse_whenTypeIsDifferent() {
		// given
		contentRepository.save(Content.builder()
			.title("Arsenal vs Chelsea")
			.type(ContentType.sport)
			.description("FA Cup Final")
			.thumbnailUrl("")
			.build());

		// when: 같은 title이지만 다른 type으로 조회
		boolean result = contentRepository.existsByTitleAndType("Arsenal vs Chelsea", ContentType.movie);

		// then
		assertThat(result).isFalse();
	}

	@Test
	@DisplayName("존재하지 않는 Content를 조회하면 false를 반환한다")
	void existsByTitleAndType_returnsFalse_whenContentDoesNotExist() {
		// when
		boolean result = contentRepository.existsByTitleAndType("Arsenal vs Chelsea", ContentType.sport);

		// then
		assertThat(result).isFalse();
	}
}
