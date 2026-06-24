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
import com.team04.mopl.content.entity.Content;
import com.team04.mopl.content.entity.ContentType;
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

    /**
     * 검증 포인트:
     * - 중복 경기는 contentRepository.save() 없이 false를 반환하는지
     */
    @Test
    @DisplayName("이미 존재하는 경기는 저장하지 않고 false를 반환한다")
    void saveIfNotExists_returnsFalse_whenDuplicateMatchExists() {
        // given
        ObjectNode eventDetail = objectMapper.createObjectNode();
        eventDetail.put("strEvent", "Arsenal vs Chelsea");

        when(contentRepository.existsByTitleAndType("Arsenal vs Chelsea", ContentType.sport)).thenReturn(true);

        // when
        boolean result = matchCollectService.saveIfNotExists(eventDetail, "Arsenal vs Chelsea");

        // then
        assertThat(result).isFalse();
        verify(contentRepository, never()).save(any());
    }

    /**
     * 검증 포인트:
     * - Content가 저장되는지
     * - Sports, Soccer, Wembley 태그 3개가 ContentTag로 저장되는지
     * - true를 반환하는지
     */
    @Test
    @DisplayName("존재하지 않는 경기는 Content와 Tag를 저장하고 true를 반환한다")
    void saveIfNotExists_returnsTrue_whenNewMatch() {
        // given
        ObjectNode eventDetail = objectMapper.createObjectNode();
        eventDetail.put("strEvent", "Arsenal vs Chelsea");
        eventDetail.put("strFilename", "FA Cup 2024");
        eventDetail.put("strThumb", "https://example.com/thumb.jpg");
        eventDetail.put("strSport", "Soccer");
        eventDetail.put("strVenue", "Wembley");

        when(contentRepository.existsByTitleAndType(any(), any())).thenReturn(false);
        when(contentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(tagRepository.findByName(any())).thenReturn(Optional.empty());
        when(tagRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        boolean result = matchCollectService.saveIfNotExists(eventDetail, "Arsenal vs Chelsea");

        // then
        assertThat(result).isTrue();
        verify(contentRepository).save(any());
        verify(contentTagRepository, times(3)).save(any()); // Sports, Soccer, Wembley
    }

    /**
     * thumbnailUrl fallback 로직:
     * strThumb → strLeagueBadge → 빈 문자열 순으로 적용
     *
     * 검증 포인트:
     * - strThumb가 없을 때 strLeagueBadge + "/small" suffix가 thumbnailUrl로 저장되는지
     */
    @Test
    @DisplayName("strThumb가 없으면 thumbnailUrl로 strLeagueBadge를 사용한다")
    void saveIfNotExists_usesLeagueBadge_whenStrThumbIsEmpty() {
        // given
        ObjectNode eventDetail = objectMapper.createObjectNode();
        eventDetail.put("strEvent", "Arsenal vs Chelsea");
        eventDetail.put("strFilename", "");
        eventDetail.put("strThumb", "");                                         // strThumb 없음
        eventDetail.put("strLeagueBadge", "https://example.com/badge.jpg");     // fallback 대상

        when(contentRepository.existsByTitleAndType(any(), any())).thenReturn(false);
        when(contentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(tagRepository.findByName(any())).thenReturn(Optional.empty());
        when(tagRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        matchCollectService.saveIfNotExists(eventDetail, "Arsenal vs Chelsea");

        // then: ArgumentCaptor로 save()에 전달된 Content 객체를 캡처해서 thumbnailUrl 검증
        ArgumentCaptor<Content> captor = ArgumentCaptor.forClass(Content.class);
        verify(contentRepository).save(captor.capture());
        assertThat(captor.getValue().getThumbnailUrl()).isEqualTo("https://example.com/badge.jpg/small");
    }

    /**
     * 검증 포인트:
     * - strThumb, strLeagueBadge 둘 다 없으면 thumbnailUrl이 빈 문자열로 저장되는지
     */
    @Test
    @DisplayName("strThumb와 strLeagueBadge 모두 없으면 thumbnailUrl은 빈 문자열이다")
    void saveIfNotExists_setsEmptyThumbnailUrl_whenBothThumbAndBadgeAreMissing() {
        // given
        ObjectNode eventDetail = objectMapper.createObjectNode();
        eventDetail.put("strEvent", "Arsenal vs Chelsea");
        eventDetail.put("strFilename", "");
        eventDetail.put("strThumb", "");        // strThumb 없음
        eventDetail.put("strLeagueBadge", "");  // strLeagueBadge도 없음 → 빈 문자열

        when(contentRepository.existsByTitleAndType(any(), any())).thenReturn(false);
        when(contentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(tagRepository.findByName(any())).thenReturn(Optional.empty());
        when(tagRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        matchCollectService.saveIfNotExists(eventDetail, "Arsenal vs Chelsea");

        // then
        ArgumentCaptor<Content> captor = ArgumentCaptor.forClass(Content.class);
        verify(contentRepository).save(captor.capture());
        assertThat(captor.getValue().getThumbnailUrl()).isEmpty();
    }

    /**
     * 검증 포인트:
     * - 이미 존재하는 태그는 tagRepository.save() 없이 재사용되는지
     * - ContentTag는 정상적으로 저장되는지
     */
    @Test
    @DisplayName("이미 존재하는 태그는 신규 생성 없이 재사용된다")
    void saveIfNotExists_reusesExistingTag_whenTagAlreadyExists() {
        // given
        ObjectNode eventDetail = objectMapper.createObjectNode();
        eventDetail.put("strEvent", "Arsenal vs Chelsea");
        eventDetail.put("strFilename", "");
        eventDetail.put("strThumb", "");
        eventDetail.put("strSport", "Soccer");
        eventDetail.put("strVenue", "");  // Venue 없음 → Sports, Soccer 2개만 연결

        Tag existingSportsTag = new Tag("Sports");
        Tag existingSoccerTag = new Tag("Soccer");

        when(contentRepository.existsByTitleAndType(any(), any())).thenReturn(false);
        when(contentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(tagRepository.findByName("Sports")).thenReturn(Optional.of(existingSportsTag)); // 기존 태그 반환
        when(tagRepository.findByName("Soccer")).thenReturn(Optional.of(existingSoccerTag)); // 기존 태그 반환

        // when
        matchCollectService.saveIfNotExists(eventDetail, "Arsenal vs Chelsea");

        // then
        verify(tagRepository, never()).save(any());          // 신규 태그 생성 없음
        verify(contentTagRepository, times(2)).save(any()); // Sports, Soccer ContentTag 저장
    }
}
