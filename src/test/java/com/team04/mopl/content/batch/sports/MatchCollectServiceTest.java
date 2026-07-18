package com.team04.mopl.content.batch.sports;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.team04.mopl.content.entity.CollectionSource;
import com.team04.mopl.content.entity.Content;
import com.team04.mopl.content.entity.Tag;
import com.team04.mopl.content.repository.ContentRepository;
import com.team04.mopl.content.repository.ContentTagRepository;
import com.team04.mopl.content.repository.TagRepository;

@ExtendWith(MockitoExtension.class)
class MatchCollectServiceTest {

	@Mock private ContentRepository contentRepository;
	@Mock private TagRepository tagRepository;
	@Mock private ContentTagRepository contentTagRepository;

	@InjectMocks
	private MatchCollectService matchCollectService;

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	@DisplayName("idEvent가 비어있으면 저장하지 않고 false를 반환한다")
	void saveIfNotExists_returnsFalse_whenIdEventIsBlank() {
		// given
		ObjectNode eventDetail = objectMapper.createObjectNode();
		eventDetail.put("idEvent", "");
		eventDetail.put("strEvent", "Arsenal vs Chelsea");

		// when
		boolean result = matchCollectService.saveIfNotExists(eventDetail, "Arsenal vs Chelsea");

		// then
		assertThat(result).isFalse();
		verify(contentRepository, never()).existsByExternalIdAndSource(any(), any());
		verify(contentRepository, never()).save(any());
	}

	@Test
	@DisplayName("이미 존재하는 경기(externalId 중복)는 저장하지 않고 false를 반환한다")
	void saveIfNotExists_returnsFalse_whenDuplicateMatchExists() {
		// given
		ObjectNode eventDetail = objectMapper.createObjectNode();
		eventDetail.put("idEvent", "event-001");
		eventDetail.put("strEvent", "Arsenal vs Chelsea");

		when(contentRepository.existsByExternalIdAndSource("event-001", CollectionSource.SPORTS_DB)).thenReturn(true);

		// when
		boolean result = matchCollectService.saveIfNotExists(eventDetail, "Arsenal vs Chelsea");

		// then
		assertThat(result).isFalse();
		verify(contentRepository, never()).save(any());
	}

	@Test
	@DisplayName("신규 경기는 Content와 Tag를 저장하고 true를 반환한다")
	void saveIfNotExists_returnsTrue_whenNewMatch() {
		// given
		ObjectNode eventDetail = objectMapper.createObjectNode();
		eventDetail.put("idEvent", "event-001");
		eventDetail.put("strEvent", "Arsenal vs Chelsea");
		eventDetail.put("strFilename", "FA Cup 2024");
		eventDetail.put("strThumb", "https://example.com/thumb.jpg");
		eventDetail.put("strSport", "Soccer");
		eventDetail.put("strVenue", "Wembley");

		when(contentRepository.existsByExternalIdAndSource(any(), any())).thenReturn(false);
		when(contentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
		when(tagRepository.findByName(any())).thenReturn(Optional.of(Tag.builder().name("태그").build()));

		// when
		boolean result = matchCollectService.saveIfNotExists(eventDetail, "Arsenal vs Chelsea");

		// then
		assertThat(result).isTrue();
		verify(contentRepository).save(any());
		verify(contentTagRepository, times(3)).save(any()); // Sports, Soccer, Wembley
	}

	@Test
	@DisplayName("저장된 Content에 externalId와 source(SPORTS_DB)가 설정된다")
	void saveIfNotExists_setsExternalIdAndSource_whenNewMatch() {
		// given
		ObjectNode eventDetail = objectMapper.createObjectNode();
		eventDetail.put("idEvent", "event-999");
		eventDetail.put("strEvent", "Arsenal vs Chelsea");
		eventDetail.put("strFilename", "");
		eventDetail.put("strThumb", "");

		when(contentRepository.existsByExternalIdAndSource(any(), any())).thenReturn(false);
		when(contentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
		when(tagRepository.findByName(any())).thenReturn(Optional.of(Tag.builder().name("태그").build()));

		// when
		matchCollectService.saveIfNotExists(eventDetail, "Arsenal vs Chelsea");

		// then
		ArgumentCaptor<Content> captor = ArgumentCaptor.forClass(Content.class);
		verify(contentRepository).save(captor.capture());
		assertThat(captor.getValue().getExternalId()).isEqualTo("event-999");
		assertThat(captor.getValue().getSource()).isEqualTo(CollectionSource.SPORTS_DB);
	}

	@Test
	@DisplayName("strThumb가 없으면 thumbnailUrl로 strLeagueBadge를 사용한다")
	void saveIfNotExists_usesLeagueBadge_whenStrThumbIsEmpty() {
		// given
		ObjectNode eventDetail = objectMapper.createObjectNode();
		eventDetail.put("idEvent", "event-001");
		eventDetail.put("strEvent", "Arsenal vs Chelsea");
		eventDetail.put("strFilename", "");
		eventDetail.put("strThumb", "");
		eventDetail.put("strLeagueBadge", "https://example.com/badge.jpg");

		when(contentRepository.existsByExternalIdAndSource(any(), any())).thenReturn(false);
		when(contentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
		when(tagRepository.findByName(any())).thenReturn(Optional.of(Tag.builder().name("태그").build()));

		// when
		matchCollectService.saveIfNotExists(eventDetail, "Arsenal vs Chelsea");

		// then
		ArgumentCaptor<Content> captor = ArgumentCaptor.forClass(Content.class);
		verify(contentRepository).save(captor.capture());
		assertThat(captor.getValue().getThumbnailUrl()).isEqualTo("https://example.com/badge.jpg/small");
	}

	@Test
	@DisplayName("strThumb와 strLeagueBadge 모두 없으면 thumbnailUrl은 빈 문자열이다")
	void saveIfNotExists_setsEmptyThumbnailUrl_whenBothThumbAndBadgeAreMissing() {
		// given
		ObjectNode eventDetail = objectMapper.createObjectNode();
		eventDetail.put("idEvent", "event-001");
		eventDetail.put("strEvent", "Arsenal vs Chelsea");
		eventDetail.put("strFilename", "");
		eventDetail.put("strThumb", "");
		eventDetail.put("strLeagueBadge", "");

		when(contentRepository.existsByExternalIdAndSource(any(), any())).thenReturn(false);
		when(contentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
		when(tagRepository.findByName(any())).thenReturn(Optional.of(Tag.builder().name("태그").build()));

		// when
		matchCollectService.saveIfNotExists(eventDetail, "Arsenal vs Chelsea");

		// then
		ArgumentCaptor<Content> captor = ArgumentCaptor.forClass(Content.class);
		verify(contentRepository).save(captor.capture());
		assertThat(captor.getValue().getThumbnailUrl()).isEmpty();
	}

	@Test
	@DisplayName("이미 존재하는 태그는 신규 생성 없이 재사용된다")
	void saveIfNotExists_reusesExistingTag_whenTagAlreadyExists() {
		// given
		ObjectNode eventDetail = objectMapper.createObjectNode();
		eventDetail.put("idEvent", "event-001");
		eventDetail.put("strEvent", "Arsenal vs Chelsea");
		eventDetail.put("strFilename", "");
		eventDetail.put("strThumb", "");
		eventDetail.put("strSport", "Soccer");
		eventDetail.put("strVenue", "");

		Tag existingSportsTag = new Tag("Sports");
		Tag existingSoccerTag = new Tag("Soccer");

		when(contentRepository.existsByExternalIdAndSource(any(), any())).thenReturn(false);
		when(contentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
		when(tagRepository.findByName("Sports")).thenReturn(Optional.of(existingSportsTag));
		when(tagRepository.findByName("Soccer")).thenReturn(Optional.of(existingSoccerTag));

		// when
		matchCollectService.saveIfNotExists(eventDetail, "Arsenal vs Chelsea");

		// then
		verify(tagRepository, never()).save(any());
		verify(contentTagRepository, times(2)).save(any()); // Sports, Soccer
	}
}
