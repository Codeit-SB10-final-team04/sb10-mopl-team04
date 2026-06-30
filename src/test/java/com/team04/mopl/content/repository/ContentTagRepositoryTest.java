package com.team04.mopl.content.repository;

import static org.assertj.core.api.Assertions.*;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import com.team04.mopl.config.JpaAuditingConfig;
import com.team04.mopl.config.QuerydslConfig;
import com.team04.mopl.content.dto.row.TagRow;
import com.team04.mopl.content.entity.Content;
import com.team04.mopl.content.entity.ContentTag;
import com.team04.mopl.content.entity.ContentType;
import com.team04.mopl.content.entity.Tag;

@DataJpaTest
@Import({JpaAuditingConfig.class, QuerydslConfig.class})
class ContentTagRepositoryTest {

	@Autowired
	private ContentTagRepository contentTagRepository;

	@Autowired
	private ContentRepository contentRepository;

	@Autowired
	private TagRepository tagRepository;

	private Content content1;
	private Content content2;
	private Tag tag1;
	private Tag tag2;
	private Tag tag3;

	@BeforeEach
	void setUp() {
		content1 = contentRepository.save(Content.builder()
			.title("콘텐츠1")
			.type(ContentType.movie)
			.description("설명1")
			.thumbnailUrl("http://thumbnail1.com")
			.build());

		content2 = contentRepository.save(Content.builder()
			.title("콘텐츠2")
			.type(ContentType.sport)
			.description("설명2")
			.thumbnailUrl("http://thumbnail2.com")
			.build());

		tag1 = tagRepository.save(Tag.builder().name("액션").build());
		tag2 = tagRepository.save(Tag.builder().name("드라마").build());
		tag3 = tagRepository.save(Tag.builder().name("스포츠").build());

		contentTagRepository.save(ContentTag.builder().content(content1).tag(tag1).build());
		contentTagRepository.save(ContentTag.builder().content(content1).tag(tag2).build());
		contentTagRepository.save(ContentTag.builder().content(content2).tag(tag3).build());
	}

	@Test
	@DisplayName("여러 contentId로 조회 시 각 contentId에 해당하는 TagRow 리스트를 반환한다")
	void findTagNamesByContentIds_returnTagRows_whenMultipleContentIdsAreProvided() {
		// given
		List<UUID> contentIds = List.of(content1.getId(), content2.getId());

		// when
		List<TagRow> result = contentTagRepository.findTagNamesByContentIds(contentIds);

		// then
		assertThat(result).hasSize(3);
		assertThat(result)
			.extracting(TagRow::contentId)
			.containsExactlyInAnyOrder(content1.getId(), content1.getId(), content2.getId());
		assertThat(result)
			.extracting(TagRow::tagName)
			.containsExactlyInAnyOrder("액션", "드라마", "스포츠");
	}

	@Test
	@DisplayName("contentId로 조회 태그명 리스트를 반환한다")
	void findTagNamesByContentId_returnTagNames_whenContentIdIsProvided() {
		// when
		List<String> result = contentTagRepository.findTagNamesByContentId(content1.getId());

		// then
		assertThat(result).hasSize(2);
		assertThat(result).containsExactlyInAnyOrder("액션", "드라마");
	}

	// ========== existsByContentAndTag ==========

	@Test
	@DisplayName("콘텐츠와 태그 연결이 존재하면 true를 반환한다")
	void existsByContentAndTag_returnTrue_whenRelationExists() {
		// when
		boolean result = contentTagRepository.existsByContentAndTag(content1, tag1);

		// then
		assertThat(result).isTrue();
	}

	@Test
	@DisplayName("콘텐츠와 태그 연결이 존재하지 않으면 false를 반환한다")
	void existsByContentAndTag_returnFalse_whenRelationNotExists() {
		// when: content1에는 tag3이 없음
		boolean result = contentTagRepository.existsByContentAndTag(content1, tag3);

		// then
		assertThat(result).isFalse();
	}
}
