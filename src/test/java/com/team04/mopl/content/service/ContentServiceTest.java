package com.team04.mopl.content.service;

import static org.assertj.core.api.AssertionsForClassTypes.*;
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

import com.team04.mopl.content.dto.response.ContentDto;
import com.team04.mopl.content.entity.Content;
import com.team04.mopl.content.exception.ContentException;
import com.team04.mopl.content.mapper.ContentMapper;
import com.team04.mopl.content.repository.ContentRepository;
import com.team04.mopl.content.repository.ContentTagRepository;

@ExtendWith(MockitoExtension.class)
class ContentServiceTest {

	@Mock
	private ContentRepository contentRepository;

	@Mock
	private ContentTagRepository contentTagRepository;

	@Mock
	private ContentMapper contentMapper;

	@InjectMocks
	private ContentService contentService;

	@Test
	@DisplayName("콘텐츠 단건 조회에 성공하면 콘텐츠 Dto를 반환한다.")
	void getContent_returnContentDto_whenValidRequest() {
		// given
		UUID contentId = UUID.randomUUID();
		Content content = mock(Content.class);
		List<String> tags = List.of("영화");
		ContentDto expectedDto = mock(ContentDto.class);

		// 삭제되지 않은 콘텐츠 조회 성공
		when(contentRepository.findByIdAndDeletedAtIsNull(contentId)).thenReturn(Optional.of(content));
		// 태그 이름 조회
		when(contentTagRepository.findTagNamesByContentId(contentId)).thenReturn(tags);
		// Mapper 변환
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

		// 삭제됐거나 없는 콘텐츠 → empty 반환
		when(contentRepository.findByIdAndDeletedAtIsNull(contentId)).thenReturn(Optional.empty());

		// when & then
		assertThatThrownBy(() -> contentService.getContent(contentId))
			.isInstanceOf(ContentException.class);

		// 예외 발생 시 태그 조회, Mapper 변환은 호출되면 안 됨
		verify(contentTagRepository, never()).findTagNamesByContentId(any());
		verify(contentMapper, never()).toDto(any(), any());

	}
}