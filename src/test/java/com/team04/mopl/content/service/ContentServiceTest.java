package com.team04.mopl.content.service;

import static org.assertj.core.api.AssertionsForClassTypes.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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

import com.team04.mopl.common.dto.CursorResponse;
import com.team04.mopl.common.storage.FileStorage;
import com.team04.mopl.content.dto.request.ContentCreateRequest;
import com.team04.mopl.content.dto.request.ContentPageRequest;
import com.team04.mopl.content.dto.request.ContentUpdateRequest;
import com.team04.mopl.content.dto.response.ContentDto;
import com.team04.mopl.content.dto.row.TagRow;
import com.team04.mopl.content.entity.Content;
import com.team04.mopl.content.entity.Tag;
import com.team04.mopl.content.exception.ContentException;
import com.team04.mopl.content.mapper.ContentMapper;
import com.team04.mopl.content.repository.ContentRepository;
import com.team04.mopl.content.repository.ContentTagRepository;
import com.team04.mopl.content.repository.TagRepository;
import com.team04.mopl.watching.service.WatchingSessionService;

@ExtendWith(MockitoExtension.class)
class ContentServiceTest {

	@Mock
	private ContentRepository contentRepository;

	@Mock
	private ContentTagRepository contentTagRepository;

	@Mock
	private TagRepository tagRepository;

	@Mock
	private FileStorage fileStorage;

	@Mock
	private ContentMapper contentMapper;

	@Mock
	private WatchingSessionService watchingSessionService;

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
		when(contentMapper.toDto(eq(content), eq(tags), anyLong())).thenReturn(expectedDto);

		// when
		ContentDto result = contentService.getContent(contentId);

		// then
		assertThat(result).isEqualTo(expectedDto);
		verify(contentRepository).findByIdAndDeletedAtIsNull(contentId);
		verify(contentTagRepository).findTagNamesByContentId(contentId);
		verify(contentMapper).toDto(eq(content), eq(tags), anyLong());
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
		verify(contentMapper, never()).toDto(any(), any(), anyLong());
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

		when(fileStorage.store(thumbnail, "thumbnails")).thenReturn("http://localhost:8080/thumbnails/thumb.png");
		when(contentRepository.save(any(Content.class))).thenAnswer(i -> i.getArgument(0));
		when(contentMapper.toDto(any(Content.class), eq(List.of()), anyLong())).thenReturn(expectedDto);

		// when
		ContentDto result = contentService.createContent(request, thumbnail);

		// then
		assertThat(result).isEqualTo(expectedDto);
		verify(fileStorage).store(thumbnail, "thumbnails");
		verify(contentRepository).save(any(Content.class));
		verify(tagRepository, never()).findAllByNameIn(any());
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

		when(fileStorage.store(thumbnail, "thumbnails")).thenReturn("http://localhost:8080/thumbnails/thumb.png");
		when(contentRepository.save(any(Content.class))).thenAnswer(i -> i.getArgument(0));
		when(tagRepository.findAllByNameIn(List.of("액션", "SF"))).thenReturn(List.of(tag1)); // 액션만 기존 존재
		when(tagRepository.save(any(Tag.class))).thenReturn(tag2);
		when(contentMapper.toDto(any(Content.class), eq(List.of("액션", "SF")), anyLong())).thenReturn(expectedDto);

		// when
		ContentDto result = contentService.createContent(request, thumbnail);

		// then
		assertThat(result).isEqualTo(expectedDto);
		verify(tagRepository).findAllByNameIn(List.of("액션", "SF"));
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

		when(fileStorage.store(thumbnail, "thumbnails")).thenThrow(new RuntimeException("썸네일 저장 실패"));

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

		when(fileStorage.store(thumbnail, "thumbnails")).thenReturn(storedUrl);
		when(contentRepository.save(any(Content.class))).thenThrow(new RuntimeException("DB 저장 실패"));

		// when & then
		assertThatThrownBy(() -> contentService.createContent(request, thumbnail))
			.isInstanceOf(RuntimeException.class);

		verify(fileStorage).delete(storedUrl);
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
		when(contentMapper.toDto(eq(c1), eq(List.of()), anyLong())).thenReturn(dto1);
		when(contentMapper.toDto(eq(c2), eq(List.of()), anyLong())).thenReturn(dto2);

		// when
		CursorResponse<ContentDto> result = contentService.getContents(req);

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
		when(contentMapper.toDto(eq(c1), eq(List.of()), anyLong())).thenReturn(dto1);
		when(contentMapper.toDto(eq(c2), eq(List.of()), anyLong())).thenReturn(dto2);

		// when
		CursorResponse<ContentDto> result = contentService.getContents(req);

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
		when(contentMapper.toDto(eq(c1), eq(List.of("액션")), anyLong())).thenReturn(expectedDto);

		// when
		CursorResponse<ContentDto> result = contentService.getContents(req);

		// then
		assertThat(result.data().get(0)).isEqualTo(expectedDto);
		verify(contentMapper).toDto(eq(c1), eq(List.of("액션")), anyLong());
	}

	@Test
	@DisplayName("콘텐츠 목록이 비어있으면 빈 데이터와 hasNext=false를 반환한다.")
	void getContents_returnEmpty_whenNoContents() {
		// given
		ContentPageRequest req = new ContentPageRequest(null, null, null, null, null, 10, "DESC", "watcherCount");

		when(contentRepository.findContents(req)).thenReturn(List.of());
		when(contentRepository.countContents(req)).thenReturn(0L);

		// when
		CursorResponse<ContentDto> result = contentService.getContents(req);

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

		when(fileStorage.store(thumbnail, "thumbnails")).thenReturn(storedUrl);
		when(contentRepository.save(any(Content.class))).thenAnswer(i -> i.getArgument(0));
		when(tagRepository.findAllByNameIn(any())).thenThrow(new RuntimeException("태그 저장 실패"));

		// when & then
		assertThatThrownBy(() -> contentService.createContent(request, thumbnail))
			.isInstanceOf(RuntimeException.class);

		verify(fileStorage).delete(storedUrl);
	}

	// ========== updateContent ==========

	@Test
	@DisplayName("콘텐츠가 존재하지 않으면 예외가 발생한다")
	void updateContent_throwException_whenContentNotFound() {
		// given
		UUID contentId = UUID.randomUUID();
		ContentUpdateRequest request = new ContentUpdateRequest("새 제목", null, null);

		when(contentRepository.findByIdAndDeletedAtIsNull(contentId)).thenReturn(Optional.empty());

		// when & then
		assertThatThrownBy(() -> contentService.updateContent(contentId, request, null))
			.isInstanceOf(ContentException.class);

		verify(fileStorage, never()).store(any(), any());
	}

	@Test
	@DisplayName("썸네일 없이 제목과 설명만 수정하면 기존 썸네일이 유지된다")
	void updateContent_updatesTitleAndDescription_whenNoThumbnail() {
		// given
		UUID contentId = UUID.randomUUID();
		Content content = mock(Content.class);
		ContentUpdateRequest request = new ContentUpdateRequest("새 제목", "새 설명", null);
		List<String> existingTags = List.of("액션");
		ContentDto expectedDto = mock(ContentDto.class);

		when(contentRepository.findByIdAndDeletedAtIsNull(contentId)).thenReturn(Optional.of(content));
		when(contentTagRepository.findTagNamesByContentId(contentId)).thenReturn(existingTags);
		when(contentMapper.toDto(eq(content), eq(existingTags), anyLong())).thenReturn(expectedDto);

		// when
		ContentDto result = contentService.updateContent(contentId, request, null);

		// then
		assertThat(result).isEqualTo(expectedDto);
		verify(content).updateTitle("새 제목");
		verify(content).updateDescription("새 설명");
		verify(content, never()).updateThumbnailUrl(any());
		verify(fileStorage, never()).store(any(), any());
		verify(fileStorage, never()).delete(any());
	}

	@Test
	@DisplayName("썸네일을 교체하면 새 썸네일을 저장하고 기존 썸네일을 삭제한다")
	void updateContent_replaceThumbnail_whenNewThumbnailProvided() {
		// given
		UUID contentId = UUID.randomUUID();
		Content content = mock(Content.class);
		ContentUpdateRequest request = new ContentUpdateRequest(null, null, null);
		MockMultipartFile newThumbnail = new MockMultipartFile(
			"thumbnail", "new.png", "image/png", "new-image".getBytes()
		);
		String oldUrl = "http://localhost:8080/thumbnails/old.png";
		String newUrl = "http://localhost:8080/thumbnails/new.png";
		ContentDto expectedDto = mock(ContentDto.class);

		when(contentRepository.findByIdAndDeletedAtIsNull(contentId)).thenReturn(Optional.of(content));
		when(content.getThumbnailUrl()).thenReturn(oldUrl);
		when(fileStorage.store(newThumbnail, "thumbnails")).thenReturn(newUrl);
		when(contentTagRepository.findTagNamesByContentId(contentId)).thenReturn(List.of());
		when(contentMapper.toDto(eq(content), eq(List.of()), anyLong())).thenReturn(expectedDto);

		// when
		ContentDto result = contentService.updateContent(contentId, request, newThumbnail);

		// then
		assertThat(result).isEqualTo(expectedDto);
		verify(fileStorage).store(newThumbnail, "thumbnails");
		verify(content).updateThumbnailUrl(newUrl);
		verify(fileStorage).delete(oldUrl);
	}

	@Test
	@DisplayName("태그 목록이 전달되면 기존 태그를 전부 삭제하고 새 태그로 교체한다")
	void updateContent_replacesTags_whenTagsProvided() {
		// given
		UUID contentId = UUID.randomUUID();
		Content content = mock(Content.class);
		ContentUpdateRequest request = new ContentUpdateRequest(null, null, List.of("액션", "SF"));
		Tag existingTag = Tag.builder().name("액션").build();
		Tag newTag = Tag.builder().name("SF").build();
		ContentDto expectedDto = mock(ContentDto.class);

		when(contentRepository.findByIdAndDeletedAtIsNull(contentId)).thenReturn(Optional.of(content));
		when(tagRepository.findAllByNameIn(List.of("액션", "SF"))).thenReturn(List.of(existingTag)); // 액션만 기존 존재
		when(tagRepository.save(any(Tag.class))).thenReturn(newTag);
		when(contentMapper.toDto(eq(content), eq(List.of("액션", "SF")), anyLong())).thenReturn(expectedDto);

		// when
		ContentDto result = contentService.updateContent(contentId, request, null);

		// then
		assertThat(result).isEqualTo(expectedDto);
		verify(contentTagRepository).deleteAllByContent(content);
		verify(tagRepository).findAllByNameIn(List.of("액션", "SF"));
		verify(tagRepository).save(any(Tag.class));
		verify(contentTagRepository).saveAll(any());
	}

	@Test
	@DisplayName("빈 태그 목록이 전달되면 기존 태그를 전부 삭제한다")
	void updateContent_deletesAllTags_whenEmptyTagsProvided() {
		// given
		UUID contentId = UUID.randomUUID();
		Content content = mock(Content.class);
		ContentUpdateRequest request = new ContentUpdateRequest(null, null, List.of());
		ContentDto expectedDto = mock(ContentDto.class);

		when(contentRepository.findByIdAndDeletedAtIsNull(contentId)).thenReturn(Optional.of(content));
		when(tagRepository.findAllByNameIn(List.of())).thenReturn(List.of());
		when(contentMapper.toDto(eq(content), eq(List.of()), anyLong())).thenReturn(expectedDto);

		// when
		ContentDto result = contentService.updateContent(contentId, request, null);

		// then
		assertThat(result).isEqualTo(expectedDto);
		verify(contentTagRepository).deleteAllByContent(content);
		verify(tagRepository, never()).save(any());
		verify(contentTagRepository).saveAll(List.of());
	}

	@Test
	@DisplayName("태그가 null이면 기존 태그를 유지한다")
	void updateContent_keepsExistingTags_whenTagsIsNull() {
		// given
		UUID contentId = UUID.randomUUID();
		Content content = mock(Content.class);
		ContentUpdateRequest request = new ContentUpdateRequest(null, null, null);
		List<String> existingTags = List.of("액션", "드라마");
		ContentDto expectedDto = mock(ContentDto.class);

		when(contentRepository.findByIdAndDeletedAtIsNull(contentId)).thenReturn(Optional.of(content));
		when(contentTagRepository.findTagNamesByContentId(contentId)).thenReturn(existingTags);
		when(contentMapper.toDto(eq(content), eq(existingTags), anyLong())).thenReturn(expectedDto);

		// when
		ContentDto result = contentService.updateContent(contentId, request, null);

		// then
		assertThat(result).isEqualTo(expectedDto);
		verify(contentTagRepository, never()).deleteAllByContent(any());
		verify(contentTagRepository, never()).saveAll(any());
	}

	@Test
	@DisplayName("DB 저장 실패 시 새로 저장한 썸네일을 삭제한다")
	void updateContent_deleteNewThumbnail_whenRepositoryFails() {
		// given
		UUID contentId = UUID.randomUUID();
		Content content = mock(Content.class);
		ContentUpdateRequest request = new ContentUpdateRequest("새 제목", null, null);
		MockMultipartFile newThumbnail = new MockMultipartFile(
			"thumbnail", "new.png", "image/png", "new-image".getBytes()
		);
		String oldUrl = "http://localhost:8080/thumbnails/old.png";
		String newUrl = "http://localhost:8080/thumbnails/new.png";

		when(contentRepository.findByIdAndDeletedAtIsNull(contentId)).thenReturn(Optional.of(content));
		when(content.getThumbnailUrl()).thenReturn(oldUrl);
		when(fileStorage.store(newThumbnail, "thumbnails")).thenReturn(newUrl);
		doThrow(new RuntimeException("DB 저장 실패")).when(content).updateTitle("새 제목"); // DB 저장 예외 실패 떤지기

		// when & then
		assertThatThrownBy(() -> contentService.updateContent(contentId, request, newThumbnail))
			.isInstanceOf(RuntimeException.class);

		verify(fileStorage).delete(newUrl);
		verify(fileStorage, never()).delete(oldUrl);
	}

	// ========== deleteContent ==========

	@Test
	@DisplayName("콘텐츠 삭제에 성공하면 markDeleted가 호출된다")
	void deleteContent_callsMarkDeleted_whenContentExists() {
		// given
		UUID contentId = UUID.randomUUID();
		Content content = mock(Content.class);

		when(contentRepository.findByIdAndDeletedAtIsNull(contentId)).thenReturn(Optional.of(content));

		// when
		contentService.deleteContent(contentId);

		// then
		verify(content).markDeleted(any());
	}

	@Test
	@DisplayName("존재하지 않는 콘텐츠를 삭제하면 예외가 발생한다")
	void deleteContent_throwException_whenContentNotFound() {
		// given
		UUID contentId = UUID.randomUUID();

		when(contentRepository.findByIdAndDeletedAtIsNull(contentId)).thenReturn(Optional.empty());

		// when & then
		assertThatThrownBy(() -> contentService.deleteContent(contentId))
			.isInstanceOf(ContentException.class);

		verify(contentRepository).findByIdAndDeletedAtIsNull(contentId);
	}
}
