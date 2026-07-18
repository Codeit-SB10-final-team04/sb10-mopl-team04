package com.team04.mopl.content.batch.tmdb;

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

@ExtendWith(MockitoExtension.class)
class TmdbDailyCollectTaskletTest {

	@Mock
	private TmdbClient tmdbClient;

	@Mock
	private TmdbContentCollectService tmdbContentCollectService;

	@InjectMocks
	private TmdbDailyCollectTasklet tmdbDailyCollectTasklet;

	private final ObjectMapper objectMapper = new ObjectMapper();

	private ChunkContext chunkContext;
	private StepContribution contribution;

	// 테스트용 movie item (movie/upcoming API의 results)
	private ObjectNode movieItem;
	// 테스트용 TV item (tv_series/on_the_air API의 results)
	private ObjectNode tvItem;
	// 테스트용 루트 JsonNode (페이지 응답)
	private ObjectNode movieRoot;
	private ObjectNode tvRoot;

	/**
	 MAX_PAGES = 10이라 getUpcomingMovies(), getOnAirTv() 각각 10회씩 호출됨
	 매 페이지 응답에 대해 extractResults() 반환값을 설정해줘야 하는데
	 기본 세팅은 빈 리스트로 두고 필요한 테스트에서만 재정의
	 */
	@BeforeEach
	void setUp() {
		contribution = mock(StepContribution.class);
		chunkContext = mock(ChunkContext.class);

		movieItem = objectMapper.createObjectNode();
		movieItem.put("title", "인터스텔라");

		tvItem = objectMapper.createObjectNode();
		tvItem.put("name", "오징어 게임");

		movieRoot = objectMapper.createObjectNode();
		movieRoot.put("_src", "movie"); // 구분용 필드 (ObjectNode.equals()는 값 비교 → 빈 노드면 Mockito가 구분 못함)

		/*
		찾아봤습니다
        Mockito stub 등록:

  		when(tmdbClient.extractResults(movieRoot)).thenReturn(List.of(movieItem));
 	 	when(tmdbClient.extractResults(tvRoot)).thenReturn(List.of(tvItem));   // 덮어씀

  		Mockito 내부에서는 이렇게 동작:

  		stub 목록:
    		extractResults({}) → List.of(movieItem)  // 첫 번째 등록
   		 	extractResults({}) → List.of(tvItem)     // 두 번째 등록 → 같은 인자({})라서 위 stub 교체

  		movieRoot와 tvRoot가 equals()로 같으니까 같은 인자로 판단 → 나중 stub이 앞 stub을 덮어씀.
        */

		tvRoot = objectMapper.createObjectNode();
		tvRoot.put("_src", "tv"); // 구분용 필드

		// MAX_PAGES(10)만큼 루프 → 각 페이지 응답 기본값을 빈 리스트
		when(tmdbClient.getUpcomingMovies(anyInt())).thenReturn(movieRoot);
		when(tmdbClient.getOnAirTv(anyInt())).thenReturn(tvRoot);
		when(tmdbClient.extractResults(any(JsonNode.class))).thenReturn(Collections.emptyList());

		// loadGenreCacheIfEmpty()는 void → doNothing 기본이므로 별도 stubbing 불필요
	}

	@Test
	@DisplayName("정상 수집 시 FINISHED를 반환한다")
	void execute_returnsFinished_whenCollectedSuccessfully() throws Exception {
		// given: 기본 세팅(빈 리스트)

		// when
		RepeatStatus status = tmdbDailyCollectTasklet.execute(contribution, chunkContext);

		// then
		assertThat(status).isEqualTo(RepeatStatus.FINISHED);
	}

	@Test
	@DisplayName("신규 item 저장 시 writeCount가 증가한다")
	void execute_incrementsWriteCount_whenServiceReturnsTrue() throws Exception {
		// given: page=1 전용 루트 노드를 별도 생성
		// setUp에서 getUpcomingMovies(anyInt()) → movieRoot 반환 → extractResults(movieRoot)는 any()로 빈 리스트 반환
		// movieRootPage1을 별도 노드로 만들어야 "page=1만 item 있고 나머지는 빈 리스트" 조건이 성립
		// (movieRoot를 재사용하면 getUpcomingMovies(anyInt())도 movieRoot → 10페이지 전부 item 반환)
		ObjectNode movieRootPage1 = objectMapper.createObjectNode();
		movieRootPage1.put("_src", "movie_page1"); // 구분용 필드
		// 수집 시 movie_page1 반환
		when(tmdbClient.getUpcomingMovies(1)).thenReturn(movieRootPage1);
		// 추출 시 movieItem 리스트 반환
		when(tmdbClient.extractResults(movieRootPage1)).thenReturn(List.of(movieItem));
		// page 2~10은 setUp의 anyInt 스텁(movieRoot) → extractResults(movieRoot)는 any()로 빈 리스트

		when(tmdbContentCollectService.saveIfNotExists(movieItem, ContentType.movie)).thenReturn(true);

		// when
		tmdbDailyCollectTasklet.execute(contribution, chunkContext);

		// then: 영화 1건 저장 → writeCount 1회 증가
		verify(contribution, times(1)).incrementWriteCount(1);
	}

	@Test
	@DisplayName("이미 존재하는 item은 writeCount를 증가시키지 않는다")
	void execute_doesNotIncrementWriteCount_whenServiceReturnsFalse() throws Exception {
		// given: page=1에 중복 item 1건
		when(tmdbClient.getUpcomingMovies(1)).thenReturn(movieRoot);
		when(tmdbClient.extractResults(movieRoot)).thenReturn(List.of(movieItem));
		// 중복이라 false 반환
		when(tmdbContentCollectService.saveIfNotExists(movieItem, ContentType.movie)).thenReturn(false);

		// when
		tmdbDailyCollectTasklet.execute(contribution, chunkContext);

		// then: skip → writeCount 증가 없음
		verify(contribution, never()).incrementWriteCount(anyInt());
	}

	@Test
	@DisplayName("각 item에 대해 saveIfNotExists()가 올바른 ContentType으로 호출된다")
	void execute_callsServiceWithCorrectContentType_forEachItem() throws Exception {
		// given: 영화/TV 각각 page=1 전용 루트 노드를 별도 생성
		// setUp의 anyInt 스텁이 movieRoot/tvRoot를 반환하므로, page=1만 별도 노드로 override해야
		// "page=1에만 item 있고 나머지는 빈 리스트" 조건이 성립
		ObjectNode movieRootPage1 = objectMapper.createObjectNode();
		movieRootPage1.put("_src", "movie_page1"); // 구분용 필드
		when(tmdbClient.getUpcomingMovies(1)).thenReturn(movieRootPage1);
		when(tmdbClient.extractResults(movieRootPage1)).thenReturn(List.of(movieItem));

		ObjectNode tvRootPage1 = objectMapper.createObjectNode();
		tvRootPage1.put("_src", "tv_page1"); // 구분용 필드
		when(tmdbClient.getOnAirTv(1)).thenReturn(tvRootPage1);
		when(tmdbClient.extractResults(tvRootPage1)).thenReturn(List.of(tvItem));

		when(tmdbContentCollectService.saveIfNotExists(any(), any())).thenReturn(true);

		// when
		tmdbDailyCollectTasklet.execute(contribution, chunkContext);

		// then: movie item은 movie 타입, tv item은 tv_series 타입으로 서비스 호출 (각 1회)
		verify(tmdbContentCollectService).saveIfNotExists(movieItem, ContentType.movie);
		verify(tmdbContentCollectService).saveIfNotExists(tvItem, ContentType.tvSeries);
	}

	@Test
	@DisplayName("MAX_PAGES(10)까지 각 엔드포인트를 호출한다")
	void execute_callsApiUpToMaxPages() throws Exception {
		// given: 기본 세팅(빈 리스트)으로 충분 → 저장 없이 루프만 검증

		// when
		tmdbDailyCollectTasklet.execute(contribution, chunkContext);

		// then: movie/upcoming, tv/on_the_air 각각 10번씩 호출
		verify(tmdbClient, times(10)).getUpcomingMovies(anyInt());
		verify(tmdbClient, times(10)).getOnAirTv(anyInt());
	}

	@Test
	@DisplayName("수집 시작 전 장르 캐시를 로드한다")
	void execute_loadsGenreCache_beforeCollecting() throws Exception {
		// given: 기본 세팅

		// when
		tmdbDailyCollectTasklet.execute(contribution, chunkContext);

		// then
		verify(tmdbContentCollectService).loadGenreCacheIfEmpty();
	}
}
