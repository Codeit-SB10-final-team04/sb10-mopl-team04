package com.team04.mopl.common.batch.metrics;

import java.util.List;

import org.springframework.batch.core.Job;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

// 애플리케이션 시작 시 모든 Batch Job의 실행 결과 Counter를 초기값 0으로 등록하는 컴포넌트
@Component
@RequiredArgsConstructor
public class BatchMetricsInitializer {

	private static final List<String> RESULTS = List.of(
		"success",
		"failure",
		"stopped"
	);

	private final List<Job> jobs;
	private final BatchMetrics batchMetrics;

	@EventListener(ApplicationReadyEvent.class)
	public void initialize() {
		jobs.forEach(job ->
			RESULTS.forEach(result ->
				batchMetrics.registerRun(job.getName(), result)
			)
		);
	}
}
