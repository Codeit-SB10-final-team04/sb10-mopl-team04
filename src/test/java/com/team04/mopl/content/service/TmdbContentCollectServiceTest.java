package com.team04.mopl.content.service;

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
import com.team04.mopl.content.client.TmdbClient;
import com.team04.mopl.content.entity.Content;
import com.team04.mopl.content.entity.ContentType;
import com.team04.mopl.content.entity.Tag;
import com.team04.mopl.content.repository.ContentRepository;
import com.team04.mopl.content.repository.ContentTagRepository;
import com.team04.mopl.content.repository.TagRepository;

@ExtendWith(MockitoExtension.class)
class TmdbContentCollectServiceTest {

    @Mock
    private ContentRepository contentRepository;
    @Mock
    private TagRepository tagRepository;
    @Mock
    private ContentTagRepository contentTagRepository;
    @Mock
    private TmdbClient tmdbClient;

    @InjectMocks
    private TmdbContentCollectService tmdbContentCollectService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("제목이 없는 item은 저장하지 않고 false를 반환한다")
    void saveIfNotExists_returnsFalse_whenTitleIsEmpty() {
        // given
        // Movie는 "title" 필드가 제목 → 빈 문자열로 세팅
        ObjectNode item = objectMapper.createObjectNode();
        item.put("title", "");

        // when
        boolean result = tmdbContentCollectService.saveIfNotExists(item, ContentType.movie);

        // then
        assertThat(result).isFalse();
        // 제목 없으면 중복 체크도 저장도 하면 안 됨
        verify(contentRepository, never()).existsByTitleAndType(any(), any());
        verify(contentRepository, never()).save(any());
    }

    @Test
    @DisplayName("이미 존재하는 Movie는 저장하지 않고 false를 반환한다")
    void saveIfNotExists_returnsFalse_whenDuplicateMovieExists() {
        // given
        ObjectNode item = objectMapper.createObjectNode();
        item.put("title", "인터스텔라");

        // 중복 존재 시뮬레이션
        when(contentRepository.existsByTitleAndType("인터스텔라", ContentType.movie)).thenReturn(true);

        // when
        boolean result = tmdbContentCollectService.saveIfNotExists(item, ContentType.movie);

        // then
        assertThat(result).isFalse();
        // 중복이면 save() 호출하면 안 됨
        verify(contentRepository, never()).save(any());
    }

    @Test
    @DisplayName("신규 Movie는 Content와 태그를 저장하고 true를 반환한다")
    void saveIfNotExists_returnsTrue_whenNewMovie() {
        // given
        ObjectNode item = objectMapper.createObjectNode();
        item.put("title", "인터스텔라");
        item.put("overview", "우주를 여행하는 이야기");
        item.put("poster_path", "/abc.jpg");

        when(contentRepository.existsByTitleAndType(any(), any())).thenReturn(false);
        // save() 호출 시 전달된 Content 객체 그대로 반환
        when(contentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(tagRepository.findByName(any())).thenReturn(Optional.empty());
        // 그대로 반환
        when(tagRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(tmdbClient.buildThumbnailUrl("/abc.jpg")).thenReturn("https://image.tmdb.org/t/p/w500/abc.jpg");

        // when
        boolean result = tmdbContentCollectService.saveIfNotExists(item, ContentType.movie);

        // then
        assertThat(result).isTrue();
        verify(contentRepository).save(any());
        // "영화" 고정 태그 1건 저장
        verify(contentTagRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("신규 TV 시리즈는 name 필드를 제목으로 저장하고 true를 반환한다")
    void saveIfNotExists_returnsTrue_whenNewTvSeries() {
        // given
        // TV는 "title" 대신 "name" 필드가 제목
        ObjectNode item = objectMapper.createObjectNode();
        item.put("name", "오징어 게임");
        item.put("overview", "생존 게임 드라마");
        item.put("poster_path", "/squid.jpg");

        when(contentRepository.existsByTitleAndType(any(), any())).thenReturn(false);
        when(contentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(tagRepository.findByName(any())).thenReturn(java.util.Optional.empty());
        when(tagRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(tmdbClient.buildThumbnailUrl("/squid.jpg")).thenReturn("https://image.tmdb.org/t/p/w500/squid.jpg");

        // when
        boolean result = tmdbContentCollectService.saveIfNotExists(item, ContentType.tv_series);

        // then
        assertThat(result).isTrue();
        // ArgumentCaptor로 실제 저장된 Content의 title이 "오징어 게임"인지 검증
        ArgumentCaptor<Content> captor = ArgumentCaptor.forClass(Content.class);
        verify(contentRepository).save(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("오징어 게임");
        assertThat(captor.getValue().getType()).isEqualTo(ContentType.tv_series);
    }

    @Test
    @DisplayName("poster_path가 없으면 thumbnailUrl은 빈 문자열로 저장된다")
    void saveIfNotExists_setsEmptyThumbnailUrl_whenPosterPathIsMissing() {
        // given
        ObjectNode item = objectMapper.createObjectNode();
        item.put("title", "포스터 없는 영화");
        item.put("overview", "");
        item.put("poster_path", ""); // poster_path 없음

        when(contentRepository.existsByTitleAndType(any(), any())).thenReturn(false);
        when(contentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(tagRepository.findByName(any())).thenReturn(java.util.Optional.empty());
        when(tagRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        // poster_path 빈 문자열 → buildThumbnailUrl이 빈 문자열 반환
        when(tmdbClient.buildThumbnailUrl("")).thenReturn("");

        // when
        tmdbContentCollectService.saveIfNotExists(item, ContentType.movie);

        // then: 저장된 Content의 thumbnailUrl이 빈 문자열인지 확인
        ArgumentCaptor<Content> captor = ArgumentCaptor.forClass(Content.class);
        verify(contentRepository).save(captor.capture());
        assertThat(captor.getValue().getThumbnailUrl()).isEmpty();
    }

    @Test
    @DisplayName("이미 존재하는 태그는 신규 생성 없이 재사용된다")
    void saveIfNotExists_reusesExistingTag_whenTagAlreadyExists() {
        // given
        ObjectNode item = objectMapper.createObjectNode();
        item.put("title", "기생충");
        item.put("overview", "");
        item.put("poster_path", "");

        Tag existingTag = new Tag("영화");

        when(contentRepository.existsByTitleAndType(any(), any())).thenReturn(false);
        when(contentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        // "영화" 태그가 이미 DB에 존재하는 상황
        when(tagRepository.findByName("영화")).thenReturn(Optional.of(existingTag));
        when(tmdbClient.buildThumbnailUrl(any())).thenReturn("");

        // when
        tmdbContentCollectService.saveIfNotExists(item, ContentType.movie);

        // then: 기존 태그 재사용 → tagRepository.save() 호출 없음
        verify(tagRepository, never()).save(any());
        // ContentTag는 정상 저장
        verify(contentTagRepository, times(1)).save(any());
    }
}
