package com.team04.mopl.auth.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.team04.mopl.auth.repository.TemporaryPasswordRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TemporaryPasswordCleanupService {

	private final TemporaryPasswordRepository temporaryPasswordRepository;

	// 메일 발송 최종 실패 시 임시 비밀번호 삭제를 즉시 커밋
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void deleteByUserId(UUID userId) {
		temporaryPasswordRepository.deleteByUser_Id(userId);
	}
}
