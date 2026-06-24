package com.team04.mopl.content.batch.step;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.repeat.RepeatStatus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.team04.mopl.content.client.TmdbClient;
import com.team04.mopl.content.entity.ContentType;
import com.team04.mopl.content.service.TmdbContentCollectService;

@ExtendWith(MockitoExtension.class)
class TmdbInitialCollectTaskletTest {

    @Mock
    private TmdbClient tmdbClient;

    @Mock
    private TmdbContentCollectService tmdbContentCollectService;

    @InjectMocks
    private TmdbInitialCollectTasklet tmdbInitialCollectTasklet;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private StepContribution contribution;
    private ChunkContext chunkContext;

    // 테스트용 영화 item (TMDB API results 배열의 단일 원소)
    private ObjectNode movieItem;
    // 테스트용 TV item
    private ObjectNode tvItem;
    // page=1 응답 루트 (movie/now_playing)
    private ObjectNode movieRoot;
    // page=1 응답 루트 (tv/on_the_air)
    private ObjectNode tvRoot;

    @BeforeEach
    void setUp() {
        contribution = mock(StepContribution.class);
        chunkContext = mock(ChunkContext.class);

        movieItem = objectMapper.createObjectNode();
        movieItem.put("title", "인터스텔라");

        tvItem = objectMapper.createObjectNode();
        tvItem.put("name", "오징어 게임");

        // movie 루트와 tv 루트를 구조적으로 다르게 설정
        movieRoot = objectMapper.createObjectNode();
        movieRoot.put("_src", "movie"); // 구분용 필드

        tvRoot = objectMapper.createObjectNode();
        tvRoot.put("_src", "tv"); // 구분용 필드
    }

    @Test
    @DisplayName("정상 수집 시 FINISHED를 반환한다")
    void execute_returnsFinished_whenCollectedSuccessfully() throws Exception {
        // given
        when(tmdbClient.getNowPlayingMovies(1)).thenReturn(movieRoot);
        when(tmdbClient.extractTotalPages(movieRoot)).thenReturn(1);
        when(tmdbClient.extractResults(movieRoot)).thenReturn(List.of(movieItem));

        when(tmdbClient.getOnAirTv(1)).thenReturn(tvRoot);
        when(tmdbClient.extractTotalPages(tvRoot)).thenReturn(1);
        when(tmdbClient.extractResults(tvRoot)).thenReturn(List.of(tvItem));

        when(tmdbContentCollectService.saveIfNotExists(any(), any())).thenReturn(true);

        // when
        RepeatStatus status = tmdbInitialCollectTasklet.execute(contribution, chunkContext);

        // then
        assertThat(status).isEqualTo(RepeatStatus.FINISHED);
    }

    @Test
    @DisplayName("saveIfNotExists()가 true 반환 시 writeCount가 증가한다")
    void execute_incrementsWriteCount_whenServiceReturnsTrue() throws Exception {
        // given: 영화 2건, TV 1건 → 총 3건 저장 성공
        ObjectNode movieItem2 = objectMapper.createObjectNode();
        movieItem2.put("title", "어벤져스");

        when(tmdbClient.getNowPlayingMovies(1)).thenReturn(movieRoot);
        when(tmdbClient.extractTotalPages(movieRoot)).thenReturn(1);
        when(tmdbClient.extractResults(movieRoot)).thenReturn(List.of(movieItem, movieItem2));

        when(tmdbClient.getOnAirTv(1)).thenReturn(tvRoot);
        when(tmdbClient.extractTotalPages(tvRoot)).thenReturn(1);
        when(tmdbClient.extractResults(tvRoot)).thenReturn(List.of(tvItem));

        when(tmdbContentCollectService.saveIfNotExists(any(), any())).thenReturn(true);

        // when
        tmdbInitialCollectTasklet.execute(contribution, chunkContext);

        // then: 총 3건 저장 성공 → incrementWriteCount 3회 호출
        verify(contribution, times(3)).incrementWriteCount(1);
    }

    @Test
    @DisplayName("total_pages가 2일 때 page=2까지 API를 호출한다")
    void execute_callsApiUpToTotalPages_whenTotalPagesIsTwo() throws Exception {
        // given: total_pages=2 → page=2 추가 호출이 일어나야 함
        ObjectNode movieRootPage2 = objectMapper.createObjectNode();
        movieRootPage2.put("_src", "movie_page2");

        ObjectNode movieItem2 = objectMapper.createObjectNode();
        movieItem2.put("title", "어벤져스");

        when(tmdbClient.getNowPlayingMovies(1)).thenReturn(movieRoot);
        when(tmdbClient.extractTotalPages(movieRoot)).thenReturn(2); // total_pages = 2
        when(tmdbClient.extractResults(movieRoot)).thenReturn(List.of(movieItem));

        // page=2 응답
        when(tmdbClient.getNowPlayingMovies(2)).thenReturn(movieRootPage2);
        when(tmdbClient.extractResults(movieRootPage2)).thenReturn(List.of(movieItem2));

        when(tmdbClient.getOnAirTv(1)).thenReturn(tvRoot);
        when(tmdbClient.extractTotalPages(tvRoot)).thenReturn(1);
        when(tmdbClient.extractResults(tvRoot)).thenReturn(Collections.emptyList());

        when(tmdbContentCollectService.saveIfNotExists(any(), any())).thenReturn(true);

        // when
        tmdbInitialCollectTasklet.execute(contribution, chunkContext);

        // then: page=1, page=2 총 2회 API 호출 검증
        verify(tmdbClient).getNowPlayingMovies(1);
        verify(tmdbClient).getNowPlayingMovies(2);
    }

    @Test
    @DisplayName("saveIfNotExists()가 false 반환 시 writeCount를 증가시키지 않는다")
    void execute_doesNotIncrementWriteCount_whenServiceReturnsFalse() throws Exception {
        // given: 모든 item이 중복으로 skip
        when(tmdbClient.getNowPlayingMovies(1)).thenReturn(movieRoot);
        when(tmdbClient.extractTotalPages(movieRoot)).thenReturn(1);
        when(tmdbClient.extractResults(movieRoot)).thenReturn(List.of(movieItem));

        when(tmdbClient.getOnAirTv(1)).thenReturn(tvRoot);
        when(tmdbClient.extractTotalPages(tvRoot)).thenReturn(1);
        when(tmdbClient.extractResults(tvRoot)).thenReturn(List.of(tvItem));

        // 중복이라 모두 false 반환
        when(tmdbContentCollectService.saveIfNotExists(any(), any())).thenReturn(false);

        // when
        tmdbInitialCollectTasklet.execute(contribution, chunkContext);

        // then: skip → writeCount 증가 없음
        verify(contribution, never()).incrementWriteCount(anyInt());
    }
}
