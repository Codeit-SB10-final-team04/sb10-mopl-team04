package com.team04.mopl.common.redis;

import java.util.concurrent.TimeUnit;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

// Redis 분산 락 유틸 — 다중 서버에서 동일 작업의 중복 실행 방지
@Slf4j
@Component
@RequiredArgsConstructor
public class DistributedLock {

	private final RedissonClient redissonClient;

	// 분산 락 획득 시도 후 작업 실행
	public boolean executeWithLock(String lockKey, long waitTime, long leaseTime, Runnable task) {
		RLock lock = redissonClient.getLock(lockKey);

		try {
			boolean acquired = lock.tryLock(waitTime, leaseTime, TimeUnit.SECONDS);

			if (!acquired) {
				log.info("[DISTRIBUTED_LOCK] 락 획득 실패, skip: key={}", lockKey);
				return false;
			}

			try {
				task.run();
				return true;
			} finally {
				if (lock.isHeldByCurrentThread()) {
					lock.unlock();
				}
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			log.warn("[DISTRIBUTED_LOCK] 락 대기 중 인터럽트: key={}", lockKey);
			return false;
		}
	}
}
