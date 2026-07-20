package com.team04.mopl.common.batch.metrics;

import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.StepExecution;
import org.springframework.batch.core.StepExecutionListener;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

// Batch Step이 끝났을 때 실행 결과를 BatchMetrics에 전달하는 Listener
@Component
@RequiredArgsConstructor
public class MoplBatchStepMetricsListener implements StepExecutionListener {

	// 물리 삭제 Step 이름
	// writeCount만 보고 저장 작업인지 삭제 작업인지 구분 불가능하기 때문
	private static final Set<String> HARD_DELETE_STEPS = Set.of(
		"contentHardDeleteStep",
		"reviewHardDeleteStep",
		"playlistHardDeleteStep",
		"notificationHardDeleteStep"
	);

	// 메트릭 기록하는 클래스
	private final BatchMetrics batchMetrics;

	// Batch Step 실행 종료 후 Spring Batch가 호출하는 메서드
	@Override
	public @Nullable ExitStatus afterStep(StepExecution stepExecution) {
		// job 이름 가져오기
		String jobName = stepExecution.getJobExecution().getJobInstance().getJobName();
		// step 이름 가져오기
		String stepName = stepExecution.getStepName();

		// Step에서 읽은 항목 수 기록
		batchMetrics.recordItems(jobName, stepName, "read", stepExecution.getReadCount());

		// write와 delete 구분
		String operation = HARD_DELETE_STEPS.contains(stepName)
			? "delete"
			: "write";

		// writer 처리 건수 기록
		batchMetrics.recordItems(jobName, stepName, operation, stepExecution.getWriteCount());

		// 필터링된 건수 기록
		batchMetrics.recordItems(jobName, stepName, "filter", stepExecution.getFilterCount());

		// 전체 skip 건수 계산 및 기록
		long skipCount = stepExecution.getReadSkipCount()
			+ stepExecution.getProcessSkipCount()
			+ stepExecution.getWriteSkipCount();
		batchMetrics.recordItems(jobName, stepName, "skip", skipCount);

		// 기존 step의 ExistStatus 변경 X
		return null;
	}
}
