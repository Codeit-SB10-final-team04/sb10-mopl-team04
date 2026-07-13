package com.team04.mopl.directmessage.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import com.team04.mopl.directmessage.dto.response.DirectMessageDto;
import com.team04.mopl.directmessage.entity.DirectMessage;
import com.team04.mopl.directmessage.mapper.DirectMessageMapper;
import com.team04.mopl.directmessage.repository.DirectMessageRepository;

@ExtendWith(MockitoExtension.class)
class DirectMessageRestoreServiceTest {

	@InjectMocks
	private DirectMessageRestoreService directMessageRestoreService;

	@Mock
	private DirectMessageRepository directMessageRepository;

	@Mock
	private DirectMessageMapper directMessageMapper;

	@Test
	@DisplayName("성공: lastEventId가 존재하면 해당 메시지 이후의 미읽음 쪽지를 반환한다.")
	void findUnreadMessagesAfter_Success() {
		// given
		UUID receiverId = UUID.randomUUID();
		UUID lastEventId = UUID.randomUUID();
		Instant lastCreatedAt = Instant.now().minusSeconds(60);

		DirectMessage lastMessage = mock(DirectMessage.class);
		given(lastMessage.getId()).willReturn(lastEventId);
		given(lastMessage.getCreatedAt()).willReturn(lastCreatedAt);

		DirectMessage unreadMessage = mock(DirectMessage.class);
		DirectMessageDto expectedDto = mock(DirectMessageDto.class);

		// 1. lastEventId 조회 모킹
		given(directMessageRepository.findByIdAndReceiverId(lastEventId, receiverId))
			.willReturn(Optional.of(lastMessage));

		// 2. 미읽음 쪽지 조회 모킹
		given(directMessageRepository.findUnreadMessagesAfter(
			eq(receiverId), eq(lastEventId), eq(lastCreatedAt), any(Pageable.class)))
			.willReturn(List.of(unreadMessage));

		// 3. Mapper 모킹
		given(directMessageMapper.toDto(unreadMessage)).willReturn(expectedDto);

		// when
		List<DirectMessageDto> result = directMessageRestoreService.findUnreadMessagesAfter(receiverId, lastEventId);

		// then
		assertThat(result).hasSize(1);
		assertThat(result.get(0)).isEqualTo(expectedDto);
	}

	@Test
	@DisplayName("성공: lastEventId에 해당하는 메시지가 없거나 수신자가 다르면 빈 리스트를 반환한다.")
	void findUnreadMessagesAfter_ReturnsEmptyList_WhenLastMessageNotFound() {
		// given
		UUID receiverId = UUID.randomUUID();
		UUID lastEventId = UUID.randomUUID();

		// 찾지 못함 (Optional.empty 반환)
		given(directMessageRepository.findByIdAndReceiverId(lastEventId, receiverId))
			.willReturn(Optional.empty());

		// when
		List<DirectMessageDto> result = directMessageRestoreService.findUnreadMessagesAfter(receiverId, lastEventId);

		// then
		assertThat(result).isEmpty();
	}
}