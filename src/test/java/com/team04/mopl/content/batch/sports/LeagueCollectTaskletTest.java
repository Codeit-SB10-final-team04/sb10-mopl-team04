package com.team04.mopl.content.batch.sports;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.team04.mopl.content.client.SportsDbClient;

@ExtendWith(MockitoExtension.class)
class LeagueCollectTaskletTest {

	@Mock
	private SportsDbClient sportsDbClient;

	@InjectMocks
	private LeagueCollectTasklet leagueCollectTasklet;

	/**
	 ObjectMapper: ApiClient를 JsonNode 타입 반환하도록 설계
	 */
	private final ObjectMapper objectMapper = new ObjectMapper();

	private ChunkContext chunkContext;
	private StepContribution contribution;
	private ExecutionContext jobExecutionContext;

	@BeforeEach
	void setUp() {
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

		// mock 체인 조립: chunkContext → stepContext (체인의 시작점)
		chunkContext = mock(ChunkContext.class);
		when(chunkContext.getStepContext()).thenReturn(stepContext);

		contribution = mock(StepContribution.class);
	}

	/**
	 * 검증 포인트:
	 * - RepeatStatus.FINISHED 반환 여부
	 * - leagueIds가 JobContext에 정확히 저장되는지
	 */
	@Test
	@DisplayName("정상 리그 목록 수집 시 leagueIds가 JobContext에 저장된다")
	void execute_savesLeagueIds_whenLeaguesCollectedSuccessfully() throws Exception {
		// given - league 생성
		ObjectNode league1 = objectMapper.createObjectNode();
		league1.put("idLeague", "4328");
		league1.put("strLeague", "English Premier League");

		ObjectNode league2 = objectMapper.createObjectNode();
		league2.put("idLeague", "4329");
		league2.put("strLeague", "La Liga");

		when(sportsDbClient.getAllLeagues()).thenReturn(List.of(league1, league2));

		// when - execute
		RepeatStatus status = leagueCollectTasklet.execute(contribution, chunkContext);

		// then - FINISHED 반환 여부, 저장된 리그 Id 반환되는지
		assertThat(status).isEqualTo(RepeatStatus.FINISHED);

		@SuppressWarnings("unchecked") // unchecked 경고 억제 (Object -> List<String> 캐스팅)
		List<String> leagueIds = (List<String>)jobExecutionContext.get("leagueIds");
		assertThat(leagueIds).containsExactly("4328", "4329");
	}

	/**
	 검증 포인트:
	 빈 idLeague는 필터링되고 유효한 항목만 leagueIds에 저장되는지
	 */
	@Test
	@DisplayName("리그 ID가 누락된 항목은 건너뛰고 유효한 항목만 저장된다")
	void execute_skipsLeague_whenLeagueIdIsMissing() throws Exception {
		// given - 정상 리그 1개, 빈 리그 1개 저장
		ObjectNode validLeague = objectMapper.createObjectNode();
		validLeague.put("idLeague", "4328");
		validLeague.put("strLeague", "English Premier League");

		ObjectNode missingIdLeague = objectMapper.createObjectNode();
		missingIdLeague.put("idLeague", "");  // 빈 ID → skip 대상
		missingIdLeague.put("strLeague", "Unknown League");

		when(sportsDbClient.getAllLeagues()).thenReturn(List.of(validLeague, missingIdLeague));

		// when - step 실행
		RepeatStatus status = leagueCollectTasklet.execute(contribution, chunkContext);

		// then - FINISHED 반환 여부, 리그 4328번 반환 여부
		assertThat(status).isEqualTo(RepeatStatus.FINISHED);
		@SuppressWarnings("unchecked") // unchecked 경고 억제 (Object -> List<String> 캐스팅 억제)
		List<String> leagueIds = (List<String>)jobExecutionContext.get("leagueIds");
		assertThat(leagueIds).containsExactly("4328");
	}

	/**
	 검증 포인트:
	 빈 리스트가 저장되고 예외 없이 FINISHED를 반환하는지
	 */
	@Test
	@DisplayName("리그 목록이 비어있으면 빈 리스트가 JobContext에 저장된다")
	void execute_savesEmptyList_whenNoLeaguesReturned() throws Exception {
		// given
		when(sportsDbClient.getAllLeagues()).thenReturn(List.of());

		// when - step 실행
		RepeatStatus status = leagueCollectTasklet.execute(contribution, chunkContext);

		// then - FINISHED 반환 여부, 빈 리스트 반환
		assertThat(status).isEqualTo(RepeatStatus.FINISHED);

		@SuppressWarnings("unchecked") List<String> leagueIds = (List<String>)jobExecutionContext.get("leagueIds");
		assertThat(leagueIds).isEmpty();
	}
}
