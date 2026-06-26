package com.team04.mopl.content.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team04.mopl.content.dto.response.ContentDto;
import com.team04.mopl.content.entity.Content;
import com.team04.mopl.content.exception.ContentErrorCode;
import com.team04.mopl.content.exception.ContentException;
import com.team04.mopl.content.mapper.ContentMapper;
import com.team04.mopl.content.repository.ContentRepository;
import com.team04.mopl.content.repository.ContentTagRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ContentService {

	private final ContentRepository contentRepository;
	private final ContentTagRepository contentTagRepository;
	private final ContentMapper contentMapper;

	public ContentDto getContent(UUID contentId) {
		Content content = getNotDeletedContentEntityOrThrow(contentId);

		List<String> tags = contentTagRepository.findTagNamesByContentId(contentId);

		return contentMapper.toDto(content, tags);
	}

	// content 조회 메서드
	private Content getNotDeletedContentEntityOrThrow(UUID contentId) {
		return contentRepository.findByIdAndDeletedAtIsNull(contentId)
			.orElseThrow(() -> new ContentException(ContentErrorCode.CONTENT_NOT_FOUND)
				.addDetail("contentId", contentId)
			);
	}
}
