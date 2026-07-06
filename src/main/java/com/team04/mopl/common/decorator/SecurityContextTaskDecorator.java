package com.team04.mopl.common.decorator;

import org.springframework.core.task.TaskDecorator;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

// 요청 스레드의 SecurityContext를 비동기 작업 스레드로 복사하는 Decorator
public class SecurityContextTaskDecorator implements TaskDecorator {

	@Override
	public Runnable decorate(Runnable runnable) {
		SecurityContext securityContext = SecurityContextHolder.getContext();

		return () -> {
			try {
				if (securityContext != null) {
					SecurityContextHolder.setContext(securityContext);
				}
				runnable.run();
			} finally {
				SecurityContextHolder.clearContext();
			}
		};
	}
}
