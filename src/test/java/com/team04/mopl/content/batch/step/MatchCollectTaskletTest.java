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
import com.team04.mopl.content.service.MatchCollectService;

@ExtendWith(MockitoExtension.class)
class MatchCollectTaskletTest {

    @Mock private SportsDbClient sportsDbClient;
    @Mock private MatchCollectService matchCollectService;

    @InjectMocks
    private MatchCollectTasklet matchCollectTasklet;

    /**
     * ObjectMapper: SportsDbClient가 JsonNode 타입으로 반환하기 때문에
     * 실제 API 응답과 동일한 타입으로 mock 데이터를 만들기 위해 사용
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    private ChunkContext chunkContext;
    private StepContribution contribution;
    private ExecutionContext jobExecutionContext;

    /**
     * Tasklet 내부 체인:
     * chunkContext → stepContext → stepExecution → jobExecution → executionContext
     *
     * ExecutionContext만 실제 객체로 만드는 이유:
     * given절에서 leagueIds를 직접 넣어두고 Tasklet이 올바르게 읽는지 검증하기 위해
     *
     * @Value 필드는 Spring 컨텍스트 없이 ReflectionTestUtils로 직접 주입
     */
    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(matchCollectTasklet, "targetSeason", "2024-2025");

        jobExecutionContext = new ExecutionContext();

        JobExecution jobExecution = mock(JobExecution.class);
        when(jobExecution.getExecutionContext()).thenReturn(jobExecutionContext);

        StepExecution stepExecution = mock(StepExecution.class);
        when(stepExecution.getJobExecution()).thenReturn(jobExecution);

        StepContext stepContext = mock(StepContext.class);
        when(stepContext.getStepExecution()).thenReturn(stepExecution);

        chunkContext = mock(ChunkContext.class);
        when(chunkContext.getStepContext()).thenReturn(stepContext);

        contribution = mock(StepContribution.class);
    }

    @Test
    @DisplayName("leagueIds가 없으면 서비스 호출 없이 FINISHED를 반환한다")
    void execute_returnsFinished_whenLeagueIdsNotInContext() throws Exception {
        // given: jobContext에 leagueIds 없음

        // when
        RepeatStatus status = matchCollectTasklet.execute(contribution, chunkContext);

        // then
        assertThat(status).isEqualTo(RepeatStatus.FINISHED);
        verify(matchCollectService, never()).saveIfNotExists(any(), any());
    }

    @Test
    @DisplayName("정상 경기 수집 시 MatchCollectService.saveIfNotExists()가 호출된다")
    void execute_callsService_whenMatchCollectedSuccessfully() throws Exception {
        // given
        jobExecutionContext.put("leagueIds", List.of("4328"));

        ObjectNode event = objectMapper.createObjectNode();
        event.put("idEvent", "1234");
        event.put("strEvent", "Arsenal vs Chelsea");

        ObjectNode eventDetail = objectMapper.createObjectNode();
        eventDetail.put("strEvent", "Arsenal vs Chelsea");

        when(sportsDbClient.getEventsBySeason("4328", "2024-2025")).thenReturn(List.of(event));
        when(sportsDbClient.getEventDetail("1234")).thenReturn(Optional.of(eventDetail));
        when(matchCollectService.saveIfNotExists(any(), any())).thenReturn(true);

        // when
        RepeatStatus status = matchCollectTasklet.execute(contribution, chunkContext);

        // then
        assertThat(status).isEqualTo(RepeatStatus.FINISHED);
        verify(matchCollectService).saveIfNotExists(eventDetail, "Arsenal vs Chelsea");
        verify(contribution).incrementWriteCount(1);
    }

    @Test
    @DisplayName("서비스가 false 반환 시 writeCount를 증가시키지 않는다")
    void execute_doesNotIncrementWriteCount_whenServiceReturnsFalse() throws Exception {
        // given
        jobExecutionContext.put("leagueIds", List.of("4328"));

        ObjectNode event = objectMapper.createObjectNode();
        event.put("idEvent", "1234");
        event.put("strEvent", "Arsenal vs Chelsea");

        when(sportsDbClient.getEventsBySeason("4328", "2024-2025")).thenReturn(List.of(event));
        when(sportsDbClient.getEventDetail("1234")).thenReturn(Optional.of(event));
        when(matchCollectService.saveIfNotExists(any(), any())).thenReturn(false);

        // when
        matchCollectTasklet.execute(contribution, chunkContext);

        // then
        verify(contribution, never()).incrementWriteCount(anyInt());
    }

    @Test
    @DisplayName("필수 필드(idEvent, strEvent)가 누락된 경기는 서비스 호출 없이 건너뛴다")
    void execute_skipsMatch_whenRequiredFieldsAreMissing() throws Exception {
        // given
        jobExecutionContext.put("leagueIds", List.of("4328"));

        ObjectNode missingEventId = objectMapper.createObjectNode();
        missingEventId.put("idEvent", "");
        missingEventId.put("strEvent", "Arsenal vs Chelsea");

        ObjectNode missingEventName = objectMapper.createObjectNode();
        missingEventName.put("idEvent", "1234");
        missingEventName.put("strEvent", "");

        when(sportsDbClient.getEventsBySeason("4328", "2024-2025"))
            .thenReturn(List.of(missingEventId, missingEventName));

        // when
        RepeatStatus status = matchCollectTasklet.execute(contribution, chunkContext);

        // then
        assertThat(status).isEqualTo(RepeatStatus.FINISHED);
        verify(matchCollectService, never()).saveIfNotExists(any(), any());
        verify(sportsDbClient, never()).getEventDetail(any());
    }
}
