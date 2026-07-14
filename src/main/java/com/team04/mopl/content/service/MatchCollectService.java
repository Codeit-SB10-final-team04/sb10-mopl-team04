package com.team04.mopl.content.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.team04.mopl.content.entity.CollectionSource;
import com.team04.mopl.content.entity.Content;
import com.team04.mopl.content.entity.ContentTag;
import com.team04.mopl.content.entity.ContentType;
import com.team04.mopl.content.entity.Tag;
import com.team04.mopl.content.repository.ContentRepository;
import com.team04.mopl.content.repository.ContentTagRepository;
import com.team04.mopl.content.repository.TagRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class MatchCollectService {

	private final ContentRepository contentRepository;
	private final TagRepository tagRepository;
	private final ContentTagRepository contentTagRepository;

	/**
	 경기 1건을 저장 -> 이미 존재하면 저장하지 않고 false를 반환

	 @param eventDetail 경기 상세 JsonNode (lookupevent.php 응답)
	 @param eventName   경기명 (idEvent 누락 시 fallback용)
	 @return 저장 성공 시 true, 중복으로 skip 시 false
	 */
	@Transactional
	public boolean saveIfNotExists(JsonNode eventDetail, String eventName) {
		String externalId = eventDetail.path("idEvent").asText("");

		if (!StringUtils.hasText(externalId)) {
			log.warn("[Batch] idEvent 누락으로 저장 불가, skip: eventName={}", eventName);
			return false;
		}

		if (contentRepository.existsByExternalIdAndSource(externalId, CollectionSource.SPORTS_DB)) {
			log.debug("[Batch] 경기 이미 존재, 건너뜀: externalId={}", externalId);
			return false;
		}

		Content content = saveContent(eventDetail, eventName, externalId);
		saveTags(content, eventDetail);

		return true;
	}

	/**
	 Content 엔티티 생성 및 저장
	 */
	private Content saveContent(JsonNode eventDetail, String eventName, String externalId) {
		String description = eventDetail.path("strFilename").asText("");

		// thumbnailUrl fallback: strThumb → strLeagueBadge → 빈 문자열
		String strThumb = eventDetail.path("strThumb").asText("");
		String rawUrl = StringUtils.hasText(strThumb)
			? strThumb
			: eventDetail.path("strLeagueBadge").asText("");
		String thumbnailUrl = StringUtils.hasText(rawUrl) ? rawUrl + "/small" : "";

		return contentRepository.save(Content.builder()
			.externalId(externalId)
			.source(CollectionSource.SPORTS_DB)
			.title(eventDetail.path("strEvent").asText(eventName))
			.type(ContentType.sport)
			.description(description)
			.thumbnailUrl(thumbnailUrl)
			.build());
	}

	/**
	 경기 Content에 태그 연결

	 저장 태그:
	 - "Sports"  : 스포츠 콘텐츠 고정 태그
	 - strSport  : 종목 (예: "Soccer")
	 - strVenue  : 홈구장 (예: "Wembley Stadium")
	 */
	private void saveTags(Content content, JsonNode eventDetail) {
		linkTag(content, "Sports");

		String sport = eventDetail.path("strSport").asText("");
		if (StringUtils.hasText(sport)) {
			linkTag(content, sport);
		}

		String venue = eventDetail.path("strVenue").asText("");
		if (StringUtils.hasText(venue)) {
			linkTag(content, venue);
		}
	}

	/**
	 태그명으로 Tag 조회 또는 생성 후 Content와 연결
	 태그가 없으면 신규 생성, 있으면 기존 태그 재사용
	 */
	private void linkTag(Content content, String tagName) {
		Tag tag;
		try {
			tag = tagRepository.findByName(tagName)
				.orElseGet(() -> tagRepository.save(new Tag(tagName)));
		} catch (DataIntegrityViolationException e) {
			tag = tagRepository.findByName(tagName)
				.orElseThrow(() -> new IllegalStateException("태그 생성 실패: " + tagName, e));
		}

		contentTagRepository.save(ContentTag.builder()
			.content(content)
			.tag(tag)
			.build());
	}

}
