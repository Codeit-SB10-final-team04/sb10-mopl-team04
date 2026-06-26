package com.team04.mopl.content.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.team04.mopl.content.dto.request.ContentCreateRequest;
import com.team04.mopl.content.dto.response.ContentDto;
import com.team04.mopl.content.entity.Content;
import com.team04.mopl.content.entity.ContentTag;
import com.team04.mopl.content.entity.ContentType;
import com.team04.mopl.content.entity.Tag;
import com.team04.mopl.content.exception.ContentErrorCode;
import com.team04.mopl.content.exception.ContentException;
import com.team04.mopl.content.mapper.ContentMapper;
import com.team04.mopl.content.repository.ContentRepository;
import com.team04.mopl.content.repository.ContentTagRepository;
import com.team04.mopl.content.repository.TagRepository;
import com.team04.mopl.content.storage.ThumbnailStorage;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ContentService {

	private final ContentRepository contentRepository;
	private final ContentTagRepository contentTagRepository;
	private final TagRepository tagRepository;
	private final ThumbnailStorage thumbnailStorage;
	private final ContentMapper contentMapper;

	public ContentDto getContent(UUID contentId) {
		Content content = getNotDeletedContentEntityOrThrow(contentId);

		List<String> tags = contentTagRepository.findTagNamesByContentId(contentId);

		return contentMapper.toDto(content, tags);
	}

	@Transactional
	public ContentDto createContent(ContentCreateRequest contentCreateRequest, MultipartFile thumbnail) {
		// 로컬에 썸네일 저장 후 Url 리턴
		String thumbnailUrl = thumbnailStorage.store(thumbnail);

		// 콘텐츠 생성
		Content content = Content.builder()
			.title(contentCreateRequest.title())
			.type(ContentType.valueOf(contentCreateRequest.type()))
			.description(contentCreateRequest.descriptions())
			.thumbnailUrl(thumbnailUrl)
			.build();

		contentRepository.save(content);

		List<String> tagNames = List.of();

		// 요청 Dto에서 tags 있을 경우 실행
		if (contentCreateRequest.tags() != null && !contentCreateRequest.tags().isEmpty()) {
			// tag 생성 or 기존 것 가져옴
			List<Tag> tags = contentCreateRequest.tags().stream()
				.map(name -> tagRepository.findByName(name)
					.orElseGet(() -> tagRepository.save(Tag.builder().name(name).build()))
				)
				.toList();

			// 중간 테이블 생성 및 저장
			List<ContentTag> contentTags = tags.stream()
				.map(tag -> ContentTag.builder().content(content).tag(tag).build())
				.toList();

			contentTagRepository.saveAll(contentTags);

			// Dto 변환을 위해 tagName으로 반환
			tagNames = tags.stream()
				.map(Tag::getName)
				.toList();
		}

		return contentMapper.toDto(content, tagNames);
	}

	// content 조회 메서드
	private Content getNotDeletedContentEntityOrThrow(UUID contentId) {
		return contentRepository.findByIdAndDeletedAtIsNull(contentId)
			.orElseThrow(() -> new ContentException(ContentErrorCode.CONTENT_NOT_FOUND)
				.addDetail("contentId", contentId)
			);
	}
}
