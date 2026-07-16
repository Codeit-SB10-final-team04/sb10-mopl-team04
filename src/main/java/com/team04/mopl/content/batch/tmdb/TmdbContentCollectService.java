package com.team04.mopl.content.batch.tmdb;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.team04.mopl.content.client.TmdbClient;
import com.team04.mopl.content.entity.CollectionSource;
import com.team04.mopl.content.entity.Content;
import com.team04.mopl.content.entity.ContentTag;
import com.team04.mopl.content.entity.ContentType;
import com.team04.mopl.content.entity.Tag;
import com.team04.mopl.content.exception.ContentErrorCode;
import com.team04.mopl.content.exception.ContentException;
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
 *   <li>장르 태그 — {@code genre_ids} 필드를 장르명으로 매핑하여 저장</li>
 * </ul>
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

	// 장르 ID → 장르명 캐시 (배치 실행 중 한 번만 로드)
	private final Map<Integer, String> genreCache = new ConcurrentHashMap<>();

	/**
	 * item 1건을 저장 → 이미 존재하면 저장하지 않고 false 반환
	 *
	 * @param item TMDB API results 배열의 단일 {@link JsonNode}
	 * @param type {@link ContentType#movie} 또는 {@link ContentType#tv_series}
	 * @return 저장 성공 시 {@code true}, 중복으로 skip 시 {@code false}
	 */
	@Transactional
	public boolean saveIfNotExists(JsonNode item, ContentType type) {
		String externalId = item.path("id").asText("");

		if (!StringUtils.hasText(externalId)) {
			log.warn("[TMDB] externalId 없는 item skip: type={}", type);
			return false;
		}

		if (contentRepository.existsByExternalIdAndSource(externalId, CollectionSource.TMDB)) {
			log.debug("[TMDB] 이미 존재, 건너뜀: externalId={}", externalId);
			return false;
		}

		String title = resolveTitle(item, type);
		if (!StringUtils.hasText(title)) {
			log.warn("[TMDB] 제목 없는 item skip: type={}", type);
			return false;
		}

		Content content = contentRepository.save(Content.builder()
			.externalId(externalId)
			.source(CollectionSource.TMDB)
			.title(title)
			.type(type)
			.description(item.path("overview").asText(""))
			.thumbnailUrl(tmdbClient.buildThumbnailUrl(item.path("poster_path").asText("")))
			.build());

		List<Integer> genreIds = extractGenreIds(item);
		saveTags(content, type, genreIds);

		return true;
	}

	/**
	 * 배치 시작 전 장르 캐시 로드
	 *
	 * <p>Tasklet에서 execute() 시작 시 호출하여 장르 맵을 미리 캐싱.
	 * 이미 로드된 경우 재로드하지 않음.
	 */
	public void loadGenreCacheIfEmpty() {
		if (!genreCache.isEmpty()) {
			return;
		}
		genreCache.putAll(tmdbClient.getMovieGenres());
		genreCache.putAll(tmdbClient.getTvGenres());
		log.info("[TMDB] 장르 캐시 로드 완료: {}건", genreCache.size());
	}

	/**
	 * Content에 고정 태그 + 장르 태그 연결
	 *
	 * <p>고정 태그: {@code "영화"} (movie) / {@code "TV 시리즈"} (tv_series)
	 * <p>장르 태그: {@code genre_ids} → 장르 캐시에서 장르명 조회 후 연결
	 */
	private void saveTags(Content content, ContentType type, List<Integer> genreIds) {
		String fixedTag = (type == ContentType.movie) ? "영화" : "TV 시리즈";
		linkTag(content, fixedTag);

		for (Integer genreId : genreIds) {
			String genreName = genreCache.get(genreId);
			if (genreName != null) {
				linkTag(content, genreName);
			}
		}
	}

	/**
	 * 태그명으로 Tag 조회 또는 생성 후 Content와 연결
	 *
	 * <p>태그가 없으면 신규 생성, 있으면 기존 태그 재사용
	 */
	private void linkTag(Content content, String tagName) {
		tagRepository.insertIgnore(java.util.UUID.randomUUID(), tagName);
		Tag tag = tagRepository.findByName(tagName)
			.orElseThrow(() -> new ContentException(ContentErrorCode.TAG_CREATION_FAILED));

		contentTagRepository.save(ContentTag.builder()
			.content(content)
			.tag(tag)
			.build());
	}

	/**
	 * TMDB item에서 genre_ids 배열 추출
	 *
	 * @param item TMDB API results 배열의 단일 {@link JsonNode}
	 * @return 장르 ID 리스트 (없으면 빈 리스트)
	 */
	private List<Integer> extractGenreIds(JsonNode item) {
		JsonNode genreIds = item.path("genre_ids");
		if (genreIds.isMissingNode() || !genreIds.isArray()) {
			return List.of();
		}

		List<Integer> ids = new ArrayList<>();
		for (JsonNode id : genreIds) {
			ids.add(id.asInt());
		}
		return ids;
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
