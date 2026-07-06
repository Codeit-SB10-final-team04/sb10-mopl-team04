package com.team04.mopl.common.decorator;

import java.util.List;

import org.springframework.core.task.TaskDecorator;

import lombok.RequiredArgsConstructor;

// 여러 TaskDecorator를 하나로 묶어두는 Decorator
@RequiredArgsConstructor
public class CompositeTaskDecorator implements TaskDecorator {

	// 적용할 Decorator 목록으로, AsyncConfig에서 순서를 정함
	private final List<TaskDecorator> taskDecorators;

	@Override
	public Runnable decorate(Runnable runnable) {
		for (int i = taskDecorators.size() - 1; i >= 0; i--) {
			runnable = taskDecorators.get(i).decorate(runnable);
		}
		
		return runnable;
	}
}
