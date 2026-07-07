package com.team04.mopl.common.decorator;

import java.util.Map;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

// 요청 스레드의 MDC 값을 비동기 작업 스레드로 복사하는 Decorator
public class MdcTaskDecorator implements TaskDecorator {

	@Override
	public Runnable decorate(Runnable runnable) {
		Map<String, String> mdcContextMap = MDC.getCopyOfContextMap();

		return () -> {
			try {
				if (mdcContextMap != null) {
					MDC.setContextMap(mdcContextMap);
				}
				runnable.run();
			} finally {
				MDC.clear();
			}
		};
	}
}
