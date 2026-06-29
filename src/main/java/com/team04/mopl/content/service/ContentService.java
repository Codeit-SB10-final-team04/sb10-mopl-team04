package com.team04.mopl.content.service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.team04.mopl.common.dto.CursorPageResponse;
import com.team04.mopl.content.dto.request.ContentCreateRequest;
import com.team04.mopl.content.dto.request.ContentPageRequest;
import com.team04.mopl.content.dto.response.ContentDto;
import com.team04.mopl.content.dto.row.TagRow;
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

	// 파일 저장, DB 저장은 한 트랜잭션에 묶일 수 없음
	@Transactional
	public ContentDto createContent(ContentCreateRequest contentCreateRequest, MultipartFile thumbnail) {
		// 로컬에 썸네일 저장 후 Url 리턴
		String thumbnailUrl = thumbnailStorage.store(thumbnail);

		// 콘텐츠 생성
		Content content = Content.builder()
			.title(contentCreateRequest.title())
			.type(ContentType.valueOf(contentCreateRequest.type()))
			.description(contentCreateRequest.description())
			.thumbnailUrl(thumbnailUrl)
			.build();

		try {
			// 콘텐츠 저장
			contentRepository.save(content);

			// 태그명
			List<String> tagNames = List.of();

			if (contentCreateRequest.tags() != null && !contentCreateRequest.tags().isEmpty()) {
				List<Tag> tags = contentCreateRequest.tags().stream()
					.map(name -> tagRepository.findByName(name)
						.orElseGet(() -> tagRepository.save(Tag.builder().name(name).build())))
					.toList();

				List<ContentTag> contentTags = tags.stream()
					.map(tag -> ContentTag.builder().content(content).tag(tag).build())
					.toList();

				contentTagRepository.saveAll(contentTags);

				tagNames = tags.stream().map(Tag::getName).toList();
			}

			return contentMapper.toDto(content, tagNames);

		} catch (Exception e) { // DB 저장 실패 시 파일을 삭제
			try {
				thumbnailStorage.delete(thumbnailUrl);
			} catch (Exception deleteEx) { // 파일 마저 삭제 실패 시 로그로 기록
				log.error("파일 삭제 실패, 배치 정리 필요. url={}", thumbnailUrl, deleteEx);
			}
			throw e;
		}
	}

	public CursorPageResponse<ContentDto> getContents(ContentPageRequest req) {
		int limit = req.limit() != null ? req.limit() : 20;
		String sortBy = req.sortBy() != null ? req.sortBy() : "watcherCount";
		String sortDirection = req.sortDirection() != null ? req.sortDirection() : "DESC";

		// limit + 1개 조회로 다음 페이지 존재 여부 판별
		List<Content> fetched = contentRepository.findContents(req);

		boolean hasNext = fetched.size() > limit;
		List<Content> page = hasNext ? fetched.subList(0, limit) : fetched;

		// 태그 일괄 조회
		List<UUID> contentIds = page.stream().map(Content::getId).toList();
		Map<UUID, List<String>> tagMap = buildTagMap(contentIds);

		List<ContentDto> data = page.stream()
			.map(c -> contentMapper.toDto(c, tagMap.getOrDefault(c.getId(), List.of())))
			.toList();

		// 다음 커서 추출
		String nextCursor = null;
		String nextIdAfter = null;
		if (hasNext) {
			Content last = page.get(page.size() - 1);
			nextCursor = extractCursor(last, sortBy);
			nextIdAfter = last.getId().toString();
		}

		long totalCount = contentRepository.countContents(req);

		return new CursorPageResponse<>(data, nextCursor, nextIdAfter, hasNext, totalCount, sortBy, sortDirection);
	}

	// contentIds로 태그명 조회 메서드
	private Map<UUID, List<String>> buildTagMap(List<UUID> contentIds) {
		if (contentIds.isEmpty()) {
			return Map.of();
		}
		return contentTagRepository.findTagNamesByContentIds(contentIds)
			.stream()
			.collect(Collectors.groupingBy(
				TagRow::contentId,
				Collectors.mapping(TagRow::tagName, Collectors.toList())
			));
	}

	// cursor 추출 메서드
	private String extractCursor(Content content, String sortBy) {
		return switch (sortBy) {
			case "averageRating" -> content.getAverageRating().toPlainString();
			case "createdAt" -> content.getCreatedAt().toString();
			default -> String.valueOf(content.getWatcherCount());
		};
	}

	// content 조회 메서드
	private Content getNotDeletedContentEntityOrThrow(UUID contentId) {
		return contentRepository.findByIdAndDeletedAtIsNull(contentId)
			.orElseThrow(() -> new ContentException(ContentErrorCode.CONTENT_NOT_FOUND)
				.addDetail("contentId", contentId)
			);
	}
}
