package com.team04.mopl.content.batch.step;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.scope.context.StepContext;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.repeat.RepeatStatus;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.team04.mopl.content.client.SportsDbClient;

@ExtendWith(MockitoExtension.class)
class MatchListCollectTaskletTest {

	@Mock private SportsDbClient sportsDbClient;

	@InjectMocks
	private MatchListCollectTasklet matchListCollectTasklet;

	private final ObjectMapper objectMapper = new ObjectMapper();

	private ChunkContext chunkContext;
	private StepContribution contribution;
	private ExecutionContext jobExecutionContext;
	private JobExecution jobExecution;

	@BeforeEach
	void setUp() {
		jobExecutionContext = new ExecutionContext();

		jobExecution = mock(JobExecution.class);
		when(jobExecution.getExecutionContext()).thenReturn(jobExecutionContext);

		StepExecution stepExecution = mock(StepExecution.class);
		when(stepExecution.getJobExecution()).thenReturn(jobExecution);

		StepContext stepContext = mock(StepContext.class);
		when(stepContext.getStepExecution()).thenReturn(stepExecution);

		chunkContext = mock(ChunkContext.class);
		when(chunkContext.getStepContext()).thenReturn(stepContext);

		contribution = mock(StepContribution.class);
	}

	private void withSeason(String season) {
		JobParameters params = new JobParametersBuilder()
			.addString("season", season)
			.toJobParameters();
		when(jobExecution.getJobParameters()).thenReturn(params);
	}

	@Test
	@DisplayName("leagueIds가 없으면 eventIds를 빈 리스트로 저장하고 FINISHED를 반환한다")
	void execute_savesEmptyEventIds_whenLeagueIdsNotInContext() throws Exception {
		// given
		withSeason("2024-2025");

		// when
		RepeatStatus status = matchListCollectTasklet.execute(contribution, chunkContext);

		// then
		assertThat(status).isEqualTo(RepeatStatus.FINISHED);
		assertThat((List<?>) jobExecutionContext.get("eventIds")).isEmpty();
		verify(sportsDbClient, never()).getEventsBySeason(any(), any());
	}

	@Test
	@DisplayName("리그별 경기 ID를 수집해 JobContext에 저장한다")
	void execute_savesEventIdsToJobContext_whenLeagueIdsExist() throws Exception {
		// given
		withSeason("2024-2025");
		jobExecutionContext.put("leagueIds", List.of("4328", "4335"));

		ObjectNode event1 = objectMapper.createObjectNode();
		event1.put("idEvent", "event-001");

		ObjectNode event2 = objectMapper.createObjectNode();
		event2.put("idEvent", "event-002");

		when(sportsDbClient.getEventsBySeason("4328", "2024-2025")).thenReturn(List.of(event1));
		when(sportsDbClient.getEventsBySeason("4335", "2024-2025")).thenReturn(List.of(event2));

		// when
		RepeatStatus status = matchListCollectTasklet.execute(contribution, chunkContext);

		// then
		assertThat(status).isEqualTo(RepeatStatus.FINISHED);
		List<String> eventIds = (List<String>) jobExecutionContext.get("eventIds");
		assertThat(eventIds).containsExactly("event-001", "event-002");
	}

	@Test
	@DisplayName("idEvent가 없는 경기는 eventIds에 포함하지 않는다")
	void execute_skipsEvent_whenIdEventIsMissing() throws Exception {
		// given
		withSeason("2024-2025");
		jobExecutionContext.put("leagueIds", List.of("4328"));

		ObjectNode validEvent = objectMapper.createObjectNode();
		validEvent.put("idEvent", "event-001");

		ObjectNode invalidEvent = objectMapper.createObjectNode();
		invalidEvent.put("idEvent", ""); // idEvent 없음

		when(sportsDbClient.getEventsBySeason("4328", "2024-2025"))
			.thenReturn(List.of(validEvent, invalidEvent));

		// when
		matchListCollectTasklet.execute(contribution, chunkContext);

		// then
		List<String> eventIds = (List<String>) jobExecutionContext.get("eventIds");
		assertThat(eventIds).containsExactly("event-001");
	}

	@Test
	@DisplayName("JobParameter season이 없으면 기본값 2025-2026으로 조회한다")
	void execute_usesDefaultSeason_whenSeasonParameterIsMissing() throws Exception {
		// given: season 파라미터 없음 → 빈 JobParameters
		when(jobExecution.getJobParameters()).thenReturn(new JobParametersBuilder().toJobParameters());
		jobExecutionContext.put("leagueIds", new ArrayList<>(List.of("4328")));
		when(sportsDbClient.getEventsBySeason(eq("4328"), eq("2025-2026"))).thenReturn(List.of());

		// when
		matchListCollectTasklet.execute(contribution, chunkContext);

		// then
		verify(sportsDbClient).getEventsBySeason("4328", "2025-2026");
	}
}
