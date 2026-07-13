package com.team04.mopl.directmessage.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team04.mopl.directmessage.dto.response.DirectMessageDto;
import com.team04.mopl.directmessage.entity.DirectMessage;
import com.team04.mopl.directmessage.mapper.DirectMessageMapper;
import com.team04.mopl.directmessage.repository.DirectMessageRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DirectMessageRestoreService {

	// 복구 최대 개수 제한
	private static final int RECOVERY_LIMIT = 500;

	private final DirectMessageRepository directMessageRepository;

	private final DirectMessageMapper directMessageMapper;

	@Transactional(readOnly = true)
	public List<DirectMessageDto> findUnreadMessagesAfter(
		UUID receiverId,
		UUID lastEventId
	) {
		log.debug("[DM_RESTORE_FIND_UNREAD_AFTER] 미읽음 쪽지 복구 조회 시작: receiverId={}, lastEventId={}, limit={}",
			receiverId, lastEventId, RECOVERY_LIMIT);

		// 1. 유효성 검증: 마지막 메시지 존재 여부 및 수신자 일치 확인
		DirectMessage message = directMessageRepository.findByIdAndReceiverId(lastEventId, receiverId).orElse(null);

		// 2. 미읽음 메시지 조회
		PageRequest pageRequest = PageRequest.of(0, RECOVERY_LIMIT);
		Instant timeLimit = Instant.now().minus(10, ChronoUnit.MINUTES);

		List<DirectMessage> messages = message == null
			// 단순 조회
			? directMessageRepository.findRecentUnreadMessages(receiverId, timeLimit, pageRequest)
			// 커서 기반 조회
			: directMessageRepository.findUnreadMessagesAfter(receiverId, message.getId(), message.getCreatedAt(),
			pageRequest);

		// 3. DTO로 변환
		List<DirectMessageDto> restoredMessages = messages.stream()
			.map(directMessageMapper::toDto)
			.toList();

		log.debug(
			"[DM_RESTORE_FIND_UNREAD_AFTER] 미읽음 쪽지 복구 조회 완료: receiverId={}, lastEventId={}, restoredCount={}",
			receiverId, lastEventId, restoredMessages.size());

		return restoredMessages;
	}
}
