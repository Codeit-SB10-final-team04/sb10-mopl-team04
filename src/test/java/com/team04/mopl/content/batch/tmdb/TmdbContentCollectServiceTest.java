package com.team04.mopl.content.batch.tmdb;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.team04.mopl.content.client.TmdbClient;
import com.team04.mopl.content.entity.CollectionSource;
import com.team04.mopl.content.entity.Content;
import com.team04.mopl.content.entity.ContentType;
import com.team04.mopl.content.entity.Tag;
import com.team04.mopl.content.repository.ContentRepository;
import com.team04.mopl.content.repository.ContentTagRepository;
import com.team04.mopl.content.repository.TagRepository;

@ExtendWith(MockitoExtension.class)
class TmdbContentCollectServiceTest {

	@Mock private ContentRepository contentRepository;
	@Mock private TagRepository tagRepository;
	@Mock private ContentTagRepository contentTagRepository;
	@Mock private TmdbClient tmdbClient;

	@InjectMocks
	private TmdbContentCollectService tmdbContentCollectService;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@BeforeEach
	void setUp() {
		// 장르 캐시 로드
		when(tmdbClient.getMovieGenres()).thenReturn(Map.of(28, "액션", 878, "SF", 18, "드라마"));
		when(tmdbClient.getTvGenres()).thenReturn(Map.of(10759, "액션 & 어드벤처", 18, "드라마"));
		tmdbContentCollectService.loadGenreCacheIfEmpty();
	}

	@Test
	@DisplayName("externalId(id 필드)가 없는 item은 저장하지 않고 false를 반환한다")
	void saveIfNotExists_returnsFalse_whenExternalIdIsEmpty() {
		// given: TMDB id 필드 없음
		ObjectNode item = objectMapper.createObjectNode();
		item.put("title", "인터스텔라");

		// when
		boolean result = tmdbContentCollectService.saveIfNotExists(item, ContentType.movie);

		// then
		assertThat(result).isFalse();
		verify(contentRepository, never()).existsByExternalIdAndSource(any(), any());
		verify(contentRepository, never()).save(any());
	}

	@Test
	@DisplayName("이미 존재하는 Movie(externalId 중복)는 저장하지 않고 false를 반환한다")
	void saveIfNotExists_returnsFalse_whenDuplicateMovieExists() {
		// given
		ObjectNode item = objectMapper.createObjectNode();
		item.put("id", 550);
		item.put("title", "인터스텔라");

		when(contentRepository.existsByExternalIdAndSource("550", CollectionSource.TMDB)).thenReturn(true);

		// when
		boolean result = tmdbContentCollectService.saveIfNotExists(item, ContentType.movie);

		// then
		assertThat(result).isFalse();
		verify(contentRepository, never()).save(any());
	}

	@Test
	@DisplayName("제목이 없는 item은 저장하지 않고 false를 반환한다")
	void saveIfNotExists_returnsFalse_whenTitleIsEmpty() {
		// given: id는 있지만 title 없음
		ObjectNode item = objectMapper.createObjectNode();
		item.put("id", 550);
		item.put("title", "");

		when(contentRepository.existsByExternalIdAndSource(any(), any())).thenReturn(false);

		// when
		boolean result = tmdbContentCollectService.saveIfNotExists(item, ContentType.movie);

		// then
		assertThat(result).isFalse();
		verify(contentRepository, never()).save(any());
	}

	@Test
	@DisplayName("신규 Movie는 Content와 고정 태그 + 장르 태그를 저장하고 true를 반환한다")
	void saveIfNotExists_returnsTrue_whenNewMovie() {
		// given
		ObjectNode item = objectMapper.createObjectNode();
		item.put("id", 550);
		item.put("title", "인터스텔라");
		item.put("overview", "우주를 여행하는 이야기");
		item.put("poster_path", "/abc.jpg");
		ArrayNode genreIds = item.putArray("genre_ids");
		genreIds.add(878);  // SF
		genreIds.add(18);   // 드라마

		when(contentRepository.existsByExternalIdAndSource(any(), any())).thenReturn(false);
		when(contentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
		when(tagRepository.findByName(any())).thenReturn(Optional.of(Tag.builder().name("태그").build()));
		when(tmdbClient.buildThumbnailUrl("/abc.jpg")).thenReturn("https://image.tmdb.org/t/p/w500/abc.jpg");

		// when
		boolean result = tmdbContentCollectService.saveIfNotExists(item, ContentType.movie);

		// then
		assertThat(result).isTrue();
		verify(contentRepository).save(any());
		verify(contentTagRepository, times(3)).save(any()); // "영화" + "SF" + "드라마"
	}

	@Test
	@DisplayName("저장된 Content에 externalId와 source(TMDB)가 설정된다")
	void saveIfNotExists_setsExternalIdAndSource_whenNewMovie() {
		// given
		ObjectNode item = objectMapper.createObjectNode();
		item.put("id", 550);
		item.put("title", "인터스텔라");
		item.put("overview", "");
		item.put("poster_path", "");

		when(contentRepository.existsByExternalIdAndSource(any(), any())).thenReturn(false);
		when(contentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
		when(tagRepository.findByName(any())).thenReturn(Optional.of(Tag.builder().name("영화").build()));
		when(tmdbClient.buildThumbnailUrl(any())).thenReturn("");

		// when
		tmdbContentCollectService.saveIfNotExists(item, ContentType.movie);

		// then
		ArgumentCaptor<Content> captor = ArgumentCaptor.forClass(Content.class);
		verify(contentRepository).save(captor.capture());
		assertThat(captor.getValue().getExternalId()).isEqualTo("550");
		assertThat(captor.getValue().getSource()).isEqualTo(CollectionSource.TMDB);
	}

	@Test
	@DisplayName("신규 TV 시리즈는 name 필드를 제목으로 저장하고 true를 반환한다")
	void saveIfNotExists_returnsTrue_whenNewTvSeries() {
		// given: TV는 "title" 대신 "name" 필드가 제목
		ObjectNode item = objectMapper.createObjectNode();
		item.put("id", 93405);
		item.put("name", "오징어 게임");
		item.put("overview", "생존 게임 드라마");
		item.put("poster_path", "/squid.jpg");
		ArrayNode genreIds = item.putArray("genre_ids");
		genreIds.add(10759);  // 액션 & 어드벤처

		when(contentRepository.existsByExternalIdAndSource(any(), any())).thenReturn(false);
		when(contentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
		when(tagRepository.findByName(any())).thenReturn(Optional.of(Tag.builder().name("태그").build()));
		when(tmdbClient.buildThumbnailUrl("/squid.jpg")).thenReturn("https://image.tmdb.org/t/p/w500/squid.jpg");

		// when
		boolean result = tmdbContentCollectService.saveIfNotExists(item, ContentType.tvSeries);

		// then
		assertThat(result).isTrue();
		ArgumentCaptor<Content> captor = ArgumentCaptor.forClass(Content.class);
		verify(contentRepository).save(captor.capture());
		assertThat(captor.getValue().getTitle()).isEqualTo("오징어 게임");
		assertThat(captor.getValue().getType()).isEqualTo(ContentType.tvSeries);
		verify(contentTagRepository, times(2)).save(any()); // "TV 시리즈" + "액션 & 어드벤처"
	}

	@Test
	@DisplayName("이미 존재하는 태그는 신규 생성 없이 재사용된다")
	void saveIfNotExists_reusesExistingTag_whenTagAlreadyExists() {
		// given: genre_ids 없는 item → 고정 태그만 저장
		ObjectNode item = objectMapper.createObjectNode();
		item.put("id", 869626);
		item.put("title", "기생충");
		item.put("overview", "");
		item.put("poster_path", "");

		Tag existingTag = new Tag("영화");

		when(contentRepository.existsByExternalIdAndSource(any(), any())).thenReturn(false);
		when(contentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
		when(tagRepository.findByName("영화")).thenReturn(Optional.of(existingTag));
		when(tmdbClient.buildThumbnailUrl(any())).thenReturn("");

		// when
		tmdbContentCollectService.saveIfNotExists(item, ContentType.movie);

		// then
		verify(tagRepository, never()).save(any());
		verify(contentTagRepository, times(1)).save(any()); // "영화" 고정 태그만
	}

	@Test
	@DisplayName("genre_ids가 없는 item은 고정 태그만 저장된다")
	void saveIfNotExists_savesOnlyFixedTag_whenGenreIdsIsMissing() {
		// given
		ObjectNode item = objectMapper.createObjectNode();
		item.put("id", 100);
		item.put("title", "제목");
		item.put("overview", "");
		item.put("poster_path", "");

		when(contentRepository.existsByExternalIdAndSource(any(), any())).thenReturn(false);
		when(contentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
		when(tagRepository.findByName(any())).thenReturn(Optional.of(Tag.builder().name("영화").build()));
		when(tmdbClient.buildThumbnailUrl(any())).thenReturn("");

		// when
		tmdbContentCollectService.saveIfNotExists(item, ContentType.movie);

		// then: "영화" 고정 태그만 1건
		verify(contentTagRepository, times(1)).save(any());
	}

	@Test
	@DisplayName("캐시에 없는 genre_id는 무시된다")
	void saveIfNotExists_ignoresUnknownGenreId() {
		// given
		ObjectNode item = objectMapper.createObjectNode();
		item.put("id", 200);
		item.put("title", "제목");
		item.put("overview", "");
		item.put("poster_path", "");
		ArrayNode genreIds = item.putArray("genre_ids");
		genreIds.add(99999); // 캐시에 없는 ID

		when(contentRepository.existsByExternalIdAndSource(any(), any())).thenReturn(false);
		when(contentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
		when(tagRepository.findByName(any())).thenReturn(Optional.of(Tag.builder().name("영화").build()));
		when(tmdbClient.buildThumbnailUrl(any())).thenReturn("");

		// when
		tmdbContentCollectService.saveIfNotExists(item, ContentType.movie);

		// then: "영화" 고정 태그만, 알 수 없는 장르는 무시
		verify(contentTagRepository, times(1)).save(any());
	}
}
