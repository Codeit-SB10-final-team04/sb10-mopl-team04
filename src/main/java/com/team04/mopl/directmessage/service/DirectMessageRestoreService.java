package com.team04.mopl.directmessage.service;

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

		// 1. 유효성 검증: 메시지 존재 여부 및 수신자 일치 확인
		DirectMessage lastMessage = directMessageRepository.findByIdAndReceiverId(lastEventId, receiverId).orElse(null);

		// 특정 수신인의 메시지가 없을 경우, 빈 리스트 반환
		if (lastMessage == null) {
			log.warn("[SSE_LAST_EVENT_NOT_FOUND] lastEventId 기준 쪽지를 찾을 수 없거나 수신자 불일치: receiverId={}, lastEventId={}",
				receiverId, lastEventId);

			return List.of();
		}

		// 2. 미읽음 메시지 조회
		List<DirectMessage> afterMessageList =
			directMessageRepository.findUnreadMessagesAfter(
				receiverId,
				lastMessage.getId(),
				lastMessage.getCreatedAt(),
				PageRequest.of(0, RECOVERY_LIMIT)
			);

		// 3. DTO로 변환
		List<DirectMessageDto> restoredMessages = afterMessageList.stream()
			.map(directMessageMapper::toDto)
			.toList();

		log.debug(
			"[DM_RESTORE_FIND_UNREAD_AFTER] 미읽음 쪽지 복구 조회 완료: receiverId={}, lastEventId={}, restoredCount={}",
			receiverId, lastEventId, restoredMessages.size());

		return restoredMessages;
	}
}
