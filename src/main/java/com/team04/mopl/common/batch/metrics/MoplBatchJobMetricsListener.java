package com.team04.mopl.common.batch.metrics;

import java.time.Instant;

import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

// Batch Job이 끝났을 때 실행 결과를 받아 BatchMetrics에 전달하는 Listener
@Component
@RequiredArgsConstructor
public class MoplBatchJobMetricsListener implements JobExecutionListener {

	// 메트릭 기록하는 클래스
	private final BatchMetrics batchMetrics;

	// Batch Job 실행 종료 후 Spring Batch가 호출하는 메서드
	@Override
	public void afterJob(JobExecution jobExecution) {
		// job 이름 가져오기
		String jobName = jobExecution.getJobInstance().getJobName();

		// Batch 상태 가져오기
		BatchStatus batchStatus = jobExecution.getStatus();
		// Batch 상태를 메트릭용 문자열로 변환
		String result = toResult(batchStatus);

		// job 실행 횟수 기록
		batchMetrics.recordRun(jobName, result);

		// Batch 성공 시 마지막 성공 시간 갱신
		if (batchStatus == BatchStatus.COMPLETED) {
			batchMetrics.updateLastSuccess(jobName, Instant.now().getEpochSecond());
		}
	}

	// Batch 상태를 메트릭용 문자열로 변환
	private String toResult(BatchStatus batchStatus) {
		return switch (batchStatus) {
			case COMPLETED -> "success";
			case STARTED -> "stopped";
			default -> "failed";
		};
	}
}
