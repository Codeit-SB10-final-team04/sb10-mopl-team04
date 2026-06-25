package com.team04.mopl.content.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.team04.mopl.content.client.TmdbClient;
import com.team04.mopl.content.entity.Content;
import com.team04.mopl.content.entity.ContentTag;
import com.team04.mopl.content.entity.ContentType;
import com.team04.mopl.content.entity.Tag;
import com.team04.mopl.content.repository.ContentRepository;
import com.team04.mopl.content.repository.ContentTagRepository;
import com.team04.mopl.content.repository.TagRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * TMDB 콘텐츠 수집 서비스
 *
 * <p>Tasklet에서 item 단위로 호출 → {@code title + type} 기준 중복 체크 후 저장.
 * 배치 흐름 제어(페이지 루프)는 Tasklet이 담당.
 *
 * <h3>저장 태그</h3>
 * <ul>
 *   <li>{@code "영화"} — movie 고정 태그</li>
 *   <li>{@code "TV 시리즈"} — tv_series 고정 태그</li>
 * </ul>
 * TODO: 장르 태그 추가 예정 ({@code genre_ids} → {@code /genre/movie/list}, {@code /genre/tv/list} API로 장르명 매핑)
 *
 * <h3>Movie vs TV 필드명 차이</h3>
 * <ul>
 *   <li>제목: {@code title} (movie) / {@code name} (tv_series)</li>
 *   <li>설명: {@code overview} (공통)</li>
 *   <li>썸네일: {@code poster_path} → {@link TmdbClient#buildThumbnailUrl}로 완성 URL 조합</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TmdbContentCollectService {

	private final ContentRepository contentRepository;
	private final TagRepository tagRepository;
	private final ContentTagRepository contentTagRepository;
	private final TmdbClient tmdbClient;

	/**
	 * item 1건을 저장 → 이미 존재하면 저장하지 않고 false 반환
	 *
	 * @param item TMDB API results 배열의 단일 {@link JsonNode}
	 * @param type {@link ContentType#movie} 또는 {@link ContentType#tv_series}
	 * @return 저장 성공 시 {@code true}, 중복으로 skip 시 {@code false}
	 */
	@Transactional
	public boolean saveIfNotExists(JsonNode item, ContentType type) {
		// type에 따라 title 필드 추출
		String title = resolveTitle(item, type);

		if (!StringUtils.hasText(title)) {
			log.warn("[TMDB] 제목 없는 item skip: type={}", type);
			return false;
		}

		if (contentRepository.existsByTitleAndType(title, type)) {
			log.debug("[TMDB] 이미 존재, 건너뜀: {}", title);
			return false;
		}

		Content content = contentRepository.save(Content.builder()
			.title(title)
			.type(type)
			.description(item.path("overview").asText("")) // overview 없으면 빈 문자열
			.thumbnailUrl(tmdbClient.buildThumbnailUrl(item.path("poster_path").asText(""))) // url 경로도 마찬가지
			.build());

		saveTags(content, type);

		return true;
	}

	/**
	 * Content에 고정 태그 연결
	 *
	 * <p>저장 태그: {@code "영화"} (movie) / {@code "TV 시리즈"} (tv_series)
	 *
	 * <p>TODO: 장르 태그 추가 예정 ({@code genre_ids} → {@code /genre/movie/list}, {@code /genre/tv/list} API로 장르명 매핑)
	 */
	private void saveTags(Content content, ContentType type) {
		String fixedTag = (type == ContentType.movie) ? "영화" : "TV 시리즈";
		linkTag(content, fixedTag);
	}

	/**
	 * 태그명으로 Tag 조회 또는 생성 후 Content와 연결
	 *
	 * <p>태그가 없으면 신규 생성, 있으면 기존 태그 재사용
	 */
	private void linkTag(Content content, String tagName) {
		Tag tag = tagRepository.findByName(tagName)
			.orElseGet(() -> tagRepository.save(new Tag(tagName)));

		contentTagRepository.save(ContentTag.builder()
			.content(content)
			.tag(tag)
			.build());
	}

	/**
	 * ContentType에 따라 제목 필드 분기
	 *
	 * <ul>
	 *   <li>Movie → {@code "title"} 필드</li>
	 *   <li>TV    → {@code "name"} 필드</li>
	 * </ul>
	 */
	private String resolveTitle(JsonNode item, ContentType type) {
		return type == ContentType.movie
			? item.path("title").asText("")
			: item.path("name").asText("");
	}
}
