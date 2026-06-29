package com.team04.mopl.content.service;

import static org.assertj.core.api.AssertionsForClassTypes.*;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import com.team04.mopl.common.dto.CursorPageResponse;
import com.team04.mopl.content.dto.request.ContentCreateRequest;
import com.team04.mopl.content.dto.request.ContentPageRequest;
import com.team04.mopl.content.dto.row.TagRow;
import com.team04.mopl.content.dto.response.ContentDto;
import com.team04.mopl.content.entity.Content;
import com.team04.mopl.content.entity.Tag;
import com.team04.mopl.content.exception.ContentException;
import com.team04.mopl.content.mapper.ContentMapper;
import com.team04.mopl.content.repository.ContentRepository;
import com.team04.mopl.content.repository.ContentTagRepository;
import com.team04.mopl.content.repository.TagRepository;
import com.team04.mopl.content.storage.ThumbnailStorage;

@ExtendWith(MockitoExtension.class)
class ContentServiceTest {

	@Mock
	private ContentRepository contentRepository;

	@Mock
	private ContentTagRepository contentTagRepository;

	@Mock
	private TagRepository tagRepository;

	@Mock
	private ThumbnailStorage thumbnailStorage;

	@Mock
	private ContentMapper contentMapper;

	@InjectMocks
	private ContentService contentService;

	// ========== getContent ==========

	@Test
	@DisplayName("콘텐츠 단건 조회에 성공하면 콘텐츠 Dto를 반환한다.")
	void getContent_returnContentDto_whenValidRequest() {
		// given
		UUID contentId = UUID.randomUUID();
		Content content = mock(Content.class);
		List<String> tags = List.of("영화");
		ContentDto expectedDto = mock(ContentDto.class);

		when(contentRepository.findByIdAndDeletedAtIsNull(contentId)).thenReturn(Optional.of(content));
		when(contentTagRepository.findTagNamesByContentId(contentId)).thenReturn(tags);
		when(contentMapper.toDto(content, tags)).thenReturn(expectedDto);

		// when
		ContentDto result = contentService.getContent(contentId);

		// then
		assertThat(result).isEqualTo(expectedDto);
		verify(contentRepository).findByIdAndDeletedAtIsNull(contentId);
		verify(contentTagRepository).findTagNamesByContentId(contentId);
		verify(contentMapper).toDto(content, tags);
	}

	@Test
	@DisplayName("존재하지 않는 콘텐츠를 조회하면 예외가 발생한다")
	void getContent_throwException_whenContentNotFound() {
		// given
		UUID contentId = UUID.randomUUID();

		when(contentRepository.findByIdAndDeletedAtIsNull(contentId)).thenReturn(Optional.empty());

		// when & then
		assertThatThrownBy(() -> contentService.getContent(contentId))
			.isInstanceOf(ContentException.class);

		verify(contentTagRepository, never()).findTagNamesByContentId(any());
		verify(contentMapper, never()).toDto(any(), any());
	}

	// ========== createContent ==========

	@Test
	@DisplayName("태그 없이 콘텐츠 생성에 성공하면 콘텐츠 Dto를 반환한다.")
	void createContent_returnContentDto_whenNoTags() {
		// given
		ContentCreateRequest request = new ContentCreateRequest(
			"movie", "인터스텔라", "우주를 여행하는 이야기", null
		);
		MockMultipartFile thumbnail = new MockMultipartFile(
			"thumbnail", "thumb.png", "image/png", "image-data".getBytes()
		);
		ContentDto expectedDto = mock(ContentDto.class);

		when(thumbnailStorage.store(thumbnail)).thenReturn("http://localhost:8080/thumbnails/thumb.png");
		when(contentRepository.save(any(Content.class))).thenAnswer(i -> i.getArgument(0));
		when(contentMapper.toDto(any(Content.class), eq(List.of()))).thenReturn(expectedDto);

		// when
		ContentDto result = contentService.createContent(request, thumbnail);

		// then
		assertThat(result).isEqualTo(expectedDto);
		verify(thumbnailStorage).store(thumbnail);
		verify(contentRepository).save(any(Content.class));
		verify(tagRepository, never()).findByName(any());
		verify(contentTagRepository, never()).saveAll(any());
	}

	@Test
	@DisplayName("태그와 함께 콘텐츠 생성에 성공하면 태그가 포함된 콘텐츠 Dto를 반환한다.")
	void createContent_returnContentDtoWithTags_whenTagsProvided() {
		// given
		ContentCreateRequest request = new ContentCreateRequest(
			"movie", "인터스텔라", "우주를 여행하는 이야기", List.of("액션", "SF")
		);
		MockMultipartFile thumbnail = new MockMultipartFile(
			"thumbnail", "thumb.png", "image/png", "image-data".getBytes()
		);
		Tag tag1 = Tag.builder().name("액션").build();
		Tag tag2 = Tag.builder().name("SF").build();
		ContentDto expectedDto = mock(ContentDto.class);

		when(thumbnailStorage.store(thumbnail)).thenReturn("http://localhost:8080/thumbnails/thumb.png");
		when(contentRepository.save(any(Content.class))).thenAnswer(i -> i.getArgument(0));
		when(tagRepository.findByName("액션")).thenReturn(Optional.of(tag1));
		when(tagRepository.findByName("SF")).thenReturn(Optional.empty());
		when(tagRepository.save(any(Tag.class))).thenReturn(tag2);
		when(contentMapper.toDto(any(Content.class), eq(List.of("액션", "SF")))).thenReturn(expectedDto);

		// when
		ContentDto result = contentService.createContent(request, thumbnail);

		// then
		assertThat(result).isEqualTo(expectedDto);
		verify(tagRepository).findByName("액션");
		verify(tagRepository).findByName("SF");
		verify(tagRepository).save(any(Tag.class)); // SF는 새로 생성
		verify(contentTagRepository).saveAll(any());
	}

	@Test
	@DisplayName("썸네일 저장에 실패하면 예외가 발생하고 콘텐츠가 저장되지 않는다.")
	void createContent_throwException_whenThumbnailStoreFails() {
		// given
		ContentCreateRequest request = new ContentCreateRequest(
			"movie", "인터스텔라", "우주를 여행하는 이야기", null
		);
		MockMultipartFile thumbnail = new MockMultipartFile(
			"thumbnail", "thumb.png", "image/png", "image-data".getBytes()
		);

		when(thumbnailStorage.store(thumbnail)).thenThrow(new RuntimeException("썸네일 저장 실패"));

		// when & then
		assertThatThrownBy(() -> contentService.createContent(request, thumbnail))
			.isInstanceOf(RuntimeException.class)
			.hasMessage("썸네일 저장 실패");

		verify(contentRepository, never()).save(any());
	}

	@Test
	@DisplayName("DB 저장 실패 시 저장된 썸네일 파일을 삭제한다.")
	void createContent_deleteThumbnail_whenRepositorySaveFails() {
		// given
		ContentCreateRequest request = new ContentCreateRequest(
			"movie", "인터스텔라", "우주를 여행하는 이야기", null
		);
		MockMultipartFile thumbnail = new MockMultipartFile(
			"thumbnail", "thumb.png", "image/png", "image-data".getBytes()
		);
		String storedUrl = "http://localhost:8080/thumbnails/thumb.png";

		when(thumbnailStorage.store(thumbnail)).thenReturn(storedUrl);
		when(contentRepository.save(any(Content.class))).thenThrow(new RuntimeException("DB 저장 실패"));

		// when & then
		assertThatThrownBy(() -> contentService.createContent(request, thumbnail))
			.isInstanceOf(RuntimeException.class);

		verify(thumbnailStorage).delete(storedUrl);
	}

	// ========== getContents ==========

	@Test
	@DisplayName("콘텐츠 목록 조회 시 hasNext=false이면 nextCursor가 null이다.")
	void getContents_returnNoNextCursor_whenLastPage() {
		// given
		ContentPageRequest req = new ContentPageRequest(null, null, null, null, null, 2, "DESC", "watcherCount");

		UUID id1 = UUID.randomUUID();
		UUID id2 = UUID.randomUUID();
		Content c1 = mock(Content.class);
		Content c2 = mock(Content.class);
		when(c1.getId()).thenReturn(id1);
		when(c2.getId()).thenReturn(id2);

		ContentDto dto1 = mock(ContentDto.class);
		ContentDto dto2 = mock(ContentDto.class);

		when(contentRepository.findContents(req)).thenReturn(List.of(c1, c2));
		when(contentRepository.countContents(req)).thenReturn(2L);
		when(contentTagRepository.findTagNamesByContentIds(List.of(id1, id2))).thenReturn(List.of());
		when(contentMapper.toDto(c1, List.of())).thenReturn(dto1);
		when(contentMapper.toDto(c2, List.of())).thenReturn(dto2);

		// when
		CursorPageResponse<ContentDto> result = contentService.getContents(req);

		// then
		assertThat(result.hasNext()).isFalse();
		assertThat(result.nextCursor()).isNull();
		assertThat(result.nextIdAfter()).isNull();
		assertThat(result.totalCount()).isEqualTo(2L);
	}

	@Test
	@DisplayName("콘텐츠 목록 조회 시 limit+1개 조회되면 hasNext=true이고 nextCursor가 설정된다.")
	void getContents_returnNextCursor_whenMorePagesExist() {
		// given: limit=2, watcherCount 기준
		ContentPageRequest req = new ContentPageRequest(null, null, null, null, null, 2, "DESC", "watcherCount");

		UUID id1 = UUID.randomUUID();
		UUID id2 = UUID.randomUUID();
		UUID id3 = UUID.randomUUID();
		Content c1 = mock(Content.class);
		Content c2 = mock(Content.class);
		Content c3 = mock(Content.class); // limit+1번째 → hasNext 판단용
		when(c1.getId()).thenReturn(id1);
		when(c2.getId()).thenReturn(id2);
		when(c2.getWatcherCount()).thenReturn(50L);

		ContentDto dto1 = mock(ContentDto.class);
		ContentDto dto2 = mock(ContentDto.class);

		// limit+1개(3개) 반환 → hasNext=true
		when(contentRepository.findContents(req)).thenReturn(List.of(c1, c2, c3));
		when(contentRepository.countContents(req)).thenReturn(5L);
		when(contentTagRepository.findTagNamesByContentIds(List.of(id1, id2))).thenReturn(List.of());
		when(contentMapper.toDto(c1, List.of())).thenReturn(dto1);
		when(contentMapper.toDto(c2, List.of())).thenReturn(dto2);

		// when
		CursorPageResponse<ContentDto> result = contentService.getContents(req);

		// then
		assertThat(result.hasNext()).isTrue();
		assertThat(result.nextCursor()).isEqualTo("50");
		assertThat(result.nextIdAfter()).isEqualTo(id2.toString());
		assertThat(result.totalCount()).isEqualTo(5L);
		assertThat(result.data().size()).isEqualTo(2);
	}

	@Test
	@DisplayName("콘텐츠 목록 조회 시 태그가 있는 콘텐츠는 태그가 포함된 Dto를 반환한다.")
	void getContents_returnDtoWithTags_whenContentHasTags() {
		// given
		ContentPageRequest req = new ContentPageRequest(null, null, null, null, null, 10, "DESC", "watcherCount");

		UUID id1 = UUID.randomUUID();
		Content c1 = mock(Content.class);
		when(c1.getId()).thenReturn(id1);

		TagRow tagRow = new TagRow(id1, "액션");
		ContentDto expectedDto = mock(ContentDto.class);

		when(contentRepository.findContents(req)).thenReturn(List.of(c1));
		when(contentRepository.countContents(req)).thenReturn(1L);
		when(contentTagRepository.findTagNamesByContentIds(List.of(id1))).thenReturn(List.of(tagRow));
		when(contentMapper.toDto(c1, List.of("액션"))).thenReturn(expectedDto);

		// when
		CursorPageResponse<ContentDto> result = contentService.getContents(req);

		// then
		assertThat(result.data().get(0)).isEqualTo(expectedDto);
		verify(contentMapper).toDto(c1, List.of("액션"));
	}

	@Test
	@DisplayName("콘텐츠 목록이 비어있으면 빈 데이터와 hasNext=false를 반환한다.")
	void getContents_returnEmpty_whenNoContents() {
		// given
		ContentPageRequest req = new ContentPageRequest(null, null, null, null, null, 10, "DESC", "watcherCount");

		when(contentRepository.findContents(req)).thenReturn(List.of());
		when(contentRepository.countContents(req)).thenReturn(0L);

		// when
		CursorPageResponse<ContentDto> result = contentService.getContents(req);

		// then
		assertThat(result.hasNext()).isFalse();
		assertThat(result.data().isEmpty()).isTrue();
		assertThat(result.totalCount()).isEqualTo(0L);
	}

	@Test
	@DisplayName("태그 저장 실패 시 저장된 썸네일 파일을 삭제한다.")
	void createContent_deleteThumbnail_whenTagSaveFails() {
		// given
		ContentCreateRequest request = new ContentCreateRequest(
			"movie", "인터스텔라", "우주를 여행하는 이야기", List.of("액션")
		);
		MockMultipartFile thumbnail = new MockMultipartFile(
			"thumbnail", "thumb.png", "image/png", "image-data".getBytes()
		);
		String storedUrl = "http://localhost:8080/thumbnails/thumb.png";

		when(thumbnailStorage.store(thumbnail)).thenReturn(storedUrl);
		when(contentRepository.save(any(Content.class))).thenAnswer(i -> i.getArgument(0));
		when(tagRepository.findByName(any())).thenThrow(new RuntimeException("태그 저장 실패"));

		// when & then
		assertThatThrownBy(() -> contentService.createContent(request, thumbnail))
			.isInstanceOf(RuntimeException.class);

		verify(thumbnailStorage).delete(storedUrl);
	}
}
