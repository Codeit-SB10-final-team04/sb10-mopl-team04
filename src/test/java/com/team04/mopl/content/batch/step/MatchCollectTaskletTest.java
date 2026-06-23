package com.team04.mopl.content.batch.step;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.team04.mopl.content.client.SportsDbClient;
import com.team04.mopl.content.entity.Content;
import com.team04.mopl.content.entity.ContentType;
import com.team04.mopl.content.entity.Tag;
import com.team04.mopl.content.repository.ContentRepository;
import com.team04.mopl.content.repository.ContentTagRepository;
import com.team04.mopl.content.repository.TagRepository;

@ExtendWith(MockitoExtension.class)
class MatchCollectTaskletTest {

    @Mock private SportsDbClient sportsDbClient;
    @Mock private ContentRepository contentRepository;
    @Mock private TagRepository tagRepository;
    @Mock private ContentTagRepository contentTagRepository;

    @InjectMocks
    private MatchCollectTasklet matchCollectTasklet;

    /**
     ObjectMapper: ApiClient를 JsonNode 타입 반환하도록 설계
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    private ChunkContext chunkContext;
    private StepContribution contribution;
    private ExecutionContext jobExecutionContext;

    @BeforeEach
    void setUp() {
        // @Value 필드 직접 주입
        ReflectionTestUtils.setField(matchCollectTasklet, "targetSeason", "2024-2025");

        // 실제 객체 - given절에서 leagueIds를 넣어두고 Tasklet이 올바르게 읽는지 검증하기 위해 실제 객체 사용
        jobExecutionContext = new ExecutionContext();

        // mock 체인 조립: jobExecution → executionContext
        JobExecution jobExecution = mock(JobExecution.class);
        when(jobExecution.getExecutionContext()).thenReturn(jobExecutionContext);

        // mock 체인 조립: stepExecution → jobExecution
        StepExecution stepExecution = mock(StepExecution.class);
        when(stepExecution.getJobExecution()).thenReturn(jobExecution);

        // mock 체인 조립: stepContext → stepExecution
        StepContext stepContext = mock(StepContext.class);
        when(stepContext.getStepExecution()).thenReturn(stepExecution);

        // mock 체인 조립: chunkContext → stepContext
        chunkContext = mock(ChunkContext.class);
        when(chunkContext.getStepContext()).thenReturn(stepContext);

        // StepContribution: writeCount 집계용 - 검증 X
        contribution = mock(StepContribution.class);
    }

    /**
     검증 포인트: leagueIds가 없으면 API 호출 및 저장 없이 FINISHED를 반환하는지
     */
    @Test
    @DisplayName("leagueIds가 없으면 저장 없이 FINISHED를 반환한다")
    void execute_returnsFinished_whenLeagueIdsNotInContext() throws Exception {
        // given: jobContext에 leagueIds 없음

        // when
        RepeatStatus status = matchCollectTasklet.execute(contribution, chunkContext);

        // then - FINISHED 반환, API 호출 X, DB 저장 X
        assertThat(status).isEqualTo(RepeatStatus.FINISHED);
        verify(contentRepository, never()).save(any());
    }

    /**
     검증 포인트:
     Content가 저장되는지,
     Sports, Soccer, Wembley 태그 3개가 ContentTag로 저장되는지
     */
    @Test
    @DisplayName("정상 경기 수집 시 Content와 Tag가 저장된다")
    void execute_savesContentAndTags_whenMatchCollectedSuccessfully() throws Exception {
        // given - league ID list
        jobExecutionContext.put("leagueIds", List.of("4328"));

        // 1. 리그 경기 임시 데이터
        ObjectNode event = objectMapper.createObjectNode();
        event.put("idEvent", "1234");
        event.put("strEvent", "Arsenal vs Chelsea");

        // 2. 경기 상세 데이터 정보 데이터
        ObjectNode eventDetail = objectMapper.createObjectNode();
        eventDetail.put("strEvent", "Arsenal vs Chelsea");
        eventDetail.put("strFilename", "FA Cup 2024 Arsenal vs Chelsea");
        eventDetail.put("strThumb", "https://example.com/thumb.jpg");
        eventDetail.put("strSport", "Soccer");
        eventDetail.put("strVenue", "Wembley");

        // league id & season 조회 시 리그 경기 임시 데이터 반환
        when(sportsDbClient.getEventsBySeason("4328", "2024-2025")).thenReturn(List.of(event));
        // 경기 상세 정보 데이터 반환
        when(sportsDbClient.getEventDetail("1234")).thenReturn(Optional.of(eventDetail));
        // 중복 검증 여부 확인 - false
        when(contentRepository.existsByTitleAndType("Arsenal vs Chelsea", ContentType.sport)).thenReturn(false);
        // Content 객체 들어오면 그대로 반환
        when(contentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(tagRepository.findByName(any())).thenReturn(Optional.empty());
        when(tagRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when - step 실행
        RepeatStatus status = matchCollectTasklet.execute(contribution, chunkContext);

        // then - FINISHED 여부, 저장 여부 검증
        assertThat(status).isEqualTo(RepeatStatus.FINISHED);
        verify(contentRepository).save(any());
        // Tag는 sports, soccer, 홈구장 명 총 3개가 저장되어야 함 - 프로토타입 기준
        verify(contentTagRepository, times(3)).save(any()); // Sports, Soccer, Wembley
    }

    /**
     검증 포인트:
     중복 경기는 contentRepository.save() 및 getEventDetail() 호출 없이 skip되는지
     */
    @Test
    @DisplayName("이미 존재하는 경기는 건너뛰고 저장하지 않는다")
    void execute_skipsMatch_whenDuplicateMatchExists() throws Exception {
        // given
        jobExecutionContext.put("leagueIds", List.of("4328"));

        // 경기 명 저장
        ObjectNode event = objectMapper.createObjectNode();
        event.put("idEvent", "1234");
        event.put("strEvent", "Arsenal vs Chelsea");

        // 해당 경기 json 반환
        when(sportsDbClient.getEventsBySeason("4328", "2024-2025")).thenReturn(List.of(event));
        // 경기가 중복될 때 - true 반환
        when(contentRepository.existsByTitleAndType("Arsenal vs Chelsea", ContentType.sport)).thenReturn(true);

        // when - step 실행
        matchCollectTasklet.execute(contribution, chunkContext);

        // then
        verify(contentRepository, never()).save(any());
        verify(sportsDbClient, never()).getEventDetail(any()); // 중복이면 상세 조회도 하지 않음
    }

    /**
     thumbnailUrl fallback 로직:
     strThumb → strLeagueBadge → 빈 문자열 순으로 적용

     검증 포인트:
     - strThumb가 없을 때 strLeagueBadge + "/small" suffix가 thumbnailUrl로 저장되는지
     */
    @Test
    @DisplayName("strThumb가 없으면 thumbnailUrl로 strLeagueBadge를 사용한다")
    void execute_usesLeagueBadge_whenStrThumbIsEmpty() throws Exception {
        // given
        jobExecutionContext.put("leagueIds", List.of("4328"));

        // 경기명 JsonNode 데이터
        ObjectNode event = objectMapper.createObjectNode();
        event.put("idEvent", "1234");
        event.put("strEvent", "Arsenal vs Chelsea");

        // 경기 상세정보 Json 데이터
        ObjectNode eventDetail = objectMapper.createObjectNode();
        eventDetail.put("strEvent", "Arsenal vs Chelsea");
        eventDetail.put("strFilename", "");
        eventDetail.put("strThumb", "");                                         // strThumb X
        eventDetail.put("strLeagueBadge", "https://example.com/badge.jpg");     // fallback 대상

        // 해당 경기 명 리스트 반환
        when(sportsDbClient.getEventsBySeason("4328", "2024-2025")).thenReturn(List.of(event));
        // 경기 상세 정보 반환
        when(sportsDbClient.getEventDetail("1234")).thenReturn(Optional.of(eventDetail));
        when(contentRepository.existsByTitleAndType(any(), any())).thenReturn(false);
        // 그대로 반환
        when(contentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(tagRepository.findByName(any())).thenReturn(Optional.empty());
        // 그대로 반환
        when(tagRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when - step 실행
        matchCollectTasklet.execute(contribution, chunkContext);

        // then: ArgumentCaptor로 save()에 전달된 Content 객체를 캡처해서 thumbnailUrl 검증
        ArgumentCaptor<Content> captor = ArgumentCaptor.forClass(Content.class);
        verify(contentRepository).save(captor.capture());
        assertThat(captor.getValue().getThumbnailUrl()).isEqualTo("https://example.com/badge.jpg/small");
    }

    /**
     검증 포인트:
     strThumb, strLeagueBadge 둘 다 없으면 thumbnailUrl이 빈 문자열로 저장되는지
     */
    @Test
    @DisplayName("strThumb와 strLeagueBadge 모두 없으면 thumbnailUrl은 빈 문자열이다")
    void execute_setsEmptyThumbnailUrl_whenBothThumbAndBadgeAreMissing() throws Exception {
        // given
        jobExecutionContext.put("leagueIds", List.of("4328"));

        // 그전 과정과 동일해서 주석 그만 적을게요 힘들어요 ㅠㅠ
        ObjectNode event = objectMapper.createObjectNode();
        event.put("idEvent", "1234");
        event.put("strEvent", "Arsenal vs Chelsea");

        ObjectNode eventDetail = objectMapper.createObjectNode();
        eventDetail.put("strEvent", "Arsenal vs Chelsea");
        eventDetail.put("strFilename", "");
        eventDetail.put("strThumb", "");        // strThumb 없음
        eventDetail.put("strLeagueBadge", "");  // strLeagueBadge도 없음 → 빈 문자열

        when(sportsDbClient.getEventsBySeason("4328", "2024-2025")).thenReturn(List.of(event));
        when(sportsDbClient.getEventDetail("1234")).thenReturn(Optional.of(eventDetail));
        when(contentRepository.existsByTitleAndType(any(), any())).thenReturn(false);
        when(contentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(tagRepository.findByName(any())).thenReturn(Optional.empty());
        when(tagRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // when
        matchCollectTasklet.execute(contribution, chunkContext);

        // then
        ArgumentCaptor<Content> captor = ArgumentCaptor.forClass(Content.class);
        verify(contentRepository).save(captor.capture());
        assertThat(captor.getValue().getThumbnailUrl()).isEmpty(); // 비어있는지 check
    }

    /**
     검증 포인트:
     idEvent 또는 strEvent가 빈 값인 경기는 save() 및 getEventDetail() 호출 없이 skip되는지
     */
    @Test
    @DisplayName("필수 필드(idEvent, strEvent)가 누락된 경기는 건너뛴다")
    void execute_skipsMatch_whenRequiredFieldsAreMissing() throws Exception {
        // given
        jobExecutionContext.put("leagueIds", List.of("4328"));

        ObjectNode missingEventId = objectMapper.createObjectNode();
        missingEventId.put("idEvent", "");  // idEvent 없음 → skip 대상
        missingEventId.put("strEvent", "Arsenal vs Chelsea");

        ObjectNode missingEventName = objectMapper.createObjectNode();
        missingEventName.put("idEvent", "1234");
        missingEventName.put("strEvent", "");  // strEvent 없음 → skip 대상

        when(sportsDbClient.getEventsBySeason("4328", "2024-2025"))
            .thenReturn(List.of(missingEventId, missingEventName));

        // when - step 실행
        matchCollectTasklet.execute(contribution, chunkContext);

        // then - 저장 X, 경기 상세 정보 호출 X
        verify(contentRepository, never()).save(any());
        verify(sportsDbClient, never()).getEventDetail(any());
    }

    /**
     * 검증 포인트:
     이미 존재하는 태그는 tagRepository.save() 없이 재사용되는지
     ContentTag는 정상적으로 저장되는지
     */
    @Test
    @DisplayName("이미 존재하는 태그는 신규 생성 없이 재사용된다")
    void execute_reusesExistingTag_whenTagAlreadyExists() throws Exception {
        // given
        jobExecutionContext.put("leagueIds", List.of("4328"));

        ObjectNode event = objectMapper.createObjectNode();
        event.put("idEvent", "1234");
        event.put("strEvent", "Arsenal vs Chelsea");

        ObjectNode eventDetail = objectMapper.createObjectNode();
        eventDetail.put("strEvent", "Arsenal vs Chelsea");
        eventDetail.put("strFilename", "");
        eventDetail.put("strThumb", "");
        eventDetail.put("strSport", "Soccer");
        eventDetail.put("strVenue", "");  // Venue 없음 → Sports, Soccer 2개만 연결

        Tag existingSportsTag = new Tag("Sports");
        Tag existingSoccerTag = new Tag("Soccer");

        when(sportsDbClient.getEventsBySeason("4328", "2024-2025")).thenReturn(List.of(event));
        when(sportsDbClient.getEventDetail("1234")).thenReturn(Optional.of(eventDetail));
        when(contentRepository.existsByTitleAndType(any(), any())).thenReturn(false);
        when(contentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(tagRepository.findByName("Sports")).thenReturn(Optional.of(existingSportsTag)); // 기존 태그 반환
        when(tagRepository.findByName("Soccer")).thenReturn(Optional.of(existingSoccerTag)); // 기존 태그 반환

        // when - step 실행
        matchCollectTasklet.execute(contribution, chunkContext);

        // then - 신규 태그 생성 X, 중간 테이블 생성 O
        verify(tagRepository, never()).save(any());          // 신규 태그 생성 없음
        verify(contentTagRepository, times(2)).save(any()); // Sports, Soccer ContentTag 저장
    }
}
