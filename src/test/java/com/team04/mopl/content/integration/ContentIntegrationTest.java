package com.team04.mopl.content.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import jakarta.persistence.EntityManager;

import com.team04.mopl.common.storage.FileStorage;
import com.team04.mopl.content.dto.request.ContentCreateRequest;
import com.team04.mopl.content.dto.request.ContentUpdateRequest;
import com.team04.mopl.content.dto.response.ContentDto;
import com.team04.mopl.content.entity.Content;
import com.team04.mopl.content.entity.ContentType;
import com.team04.mopl.content.exception.ContentException;
import com.team04.mopl.content.repository.ContentRepository;
import com.team04.mopl.content.repository.ContentTagRepository;
import com.team04.mopl.content.repository.TagRepository;
import com.team04.mopl.content.service.ContentService;
import com.team04.mopl.review.entity.Review;
import com.team04.mopl.review.repository.ReviewRepository;
import com.team04.mopl.support.IntegrationTestBase;
import com.team04.mopl.user.entity.User;
import com.team04.mopl.user.repository.UserRepository;

@Transactional
class ContentIntegrationTest extends IntegrationTestBase {

	@Autowired
	private ContentService contentService;

	@Autowired
	private ContentRepository contentRepository;

	@Autowired
	private ContentTagRepository contentTagRepository;

	@Autowired
	private TagRepository tagRepository;

	@Autowired
	private ReviewRepository reviewRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private EntityManager entityManager;

	@MockitoBean
	private FileStorage fileStorage;

	private MockMultipartFile thumbnail;

	@BeforeEach
	void setUp() {
		thumbnail = new MockMultipartFile("thumbnail", "test.png", "image/png", "img".getBytes());
		given(fileStorage.store(any(MultipartFile.class), anyString())).willReturn("https://cdn.test/thumb.png");
	}

	@Test
	@DisplayName("태그 없이 콘텐츠를 생성하면 DB에 저장된다")
	void createContent_savesToDb_whenNoTags() {
		ContentCreateRequest request = new ContentCreateRequest("movie", "인터스텔라", "우주 이야기", null);

		ContentDto result = contentService.createContent(request, thumbnail);

		assertThat(result.id()).isNotNull();
		assertThat(result.title()).isEqualTo("인터스텔라");
		assertThat(result.type()).isEqualTo(ContentType.movie);
		assertThat(result.description()).isEqualTo("우주 이야기");

		Content saved = contentRepository.findById(result.id()).orElseThrow();
		assertThat(saved.getTitle()).isEqualTo("인터스텔라");
	}

	@Test
	@DisplayName("태그와 함께 콘텐츠를 생성하면 Tag, ContentTag가 저장된다")
	void createContent_savesTags_whenTagsProvided() {
		ContentCreateRequest request = new ContentCreateRequest("movie", "어벤져스", "히어로", List.of("액션", "SF"));

		ContentDto result = contentService.createContent(request, thumbnail);

		List<String> tags = contentTagRepository.findTagNamesByContentId(result.id());
		assertThat(tags).containsExactlyInAnyOrder("액션", "SF");
		assertThat(tagRepository.findByName("액션")).isPresent();
		assertThat(tagRepository.findByName("SF")).isPresent();
	}

	@Test
	@DisplayName("콘텐츠 단건 조회 시 type 태그는 응답에서 제외된다")
	void getContent_excludesTypeTag() {
		ContentCreateRequest request = new ContentCreateRequest("movie", "기생충", "계단", List.of("영화", "드라마"));

		ContentDto created = contentService.createContent(request, thumbnail);
		ContentDto result = contentService.getContent(created.id());

		assertThat(result.tags()).containsExactly("드라마");
		assertThat(result.tags()).doesNotContain("영화");
	}

	@Test
	@DisplayName("콘텐츠 제목과 설명을 수정하면 DB에 반영된다")
	void updateContent_updatesFields() {
		ContentCreateRequest createReq = new ContentCreateRequest("movie", "원래 제목", "원래 설명", null);
		ContentDto created = contentService.createContent(createReq, thumbnail);

		ContentUpdateRequest updateReq = new ContentUpdateRequest("새 제목", "새 설명", null);
		ContentDto updated = contentService.updateContent(created.id(), updateReq, null);

		assertThat(updated.title()).isEqualTo("새 제목");
		assertThat(updated.description()).isEqualTo("새 설명");

		Content saved = contentRepository.findById(created.id()).orElseThrow();
		assertThat(saved.getTitle()).isEqualTo("새 제목");
	}

	@Test
	@DisplayName("태그를 수정하면 차집합으로 추가/삭제된다")
	void updateContent_updatesTags_withDiff() {
		ContentCreateRequest createReq = new ContentCreateRequest("movie", "태그 테스트", "설명", List.of("A", "B"));
		ContentDto created = contentService.createContent(createReq, thumbnail);

		// [A, B] → [B, C]: A 삭제, C 추가
		ContentUpdateRequest updateReq = new ContentUpdateRequest(null, null, List.of("B", "C"));
		contentService.updateContent(created.id(), updateReq, null);

		List<String> tags = contentTagRepository.findTagNamesByContentId(created.id());
		assertThat(tags).containsExactlyInAnyOrder("B", "C");
	}

	@Test
	@DisplayName("콘텐츠를 삭제하면 연관 리뷰도 soft delete된다")
	void deleteContent_softDeletesRelatedReviews() {
		// 콘텐츠 + 유저 + 리뷰 생성
		Content content = contentRepository.save(Content.builder()
			.title("삭제될 콘텐츠").type(ContentType.movie)
			.description("설명").thumbnailUrl("url").build());

		User user = createUser("리뷰어", "reviewer@test.com");
		Review review = reviewRepository.save(Review.builder()
			.user(user).content(content).text("좋아요").rating((short) 5).build());

		contentService.deleteContent(content.getId());

		// @Modifying 쿼리는 영속성 컨텍스트를 거치지 않으므로 캐시 초기화
		entityManager.flush();
		entityManager.clear();

		// 콘텐츠 soft delete 확인
		assertThat(contentRepository.findByIdAndDeletedAtIsNull(content.getId())).isEmpty();

		// 리뷰 soft delete 확인
		Review deletedReview = reviewRepository.findById(review.getId()).orElseThrow();
		assertThat(deletedReview.getDeletedAt()).isNotNull();
	}

	@Test
	@DisplayName("삭제된 콘텐츠를 조회하면 예외가 발생한다")
	void getContent_throwsException_whenDeleted() {
		Content content = contentRepository.save(Content.builder()
			.title("삭제 대상").type(ContentType.movie)
			.description("설명").thumbnailUrl("url").build());

		contentService.deleteContent(content.getId());

		assertThatThrownBy(() -> contentService.getContent(content.getId()))
			.isInstanceOf(ContentException.class);
	}

	private User createUser(String name, String email) {
		UUID userId = UUID.randomUUID();
		jdbcTemplate.update("""
			INSERT INTO users (id, name, email, email_type, role, is_locked, created_at, updated_at)
			VALUES (?, ?, ?, 'REAL', 'USER', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
			""", userId, name, email);
		return userRepository.findById(userId).orElseThrow();
	}
}
