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
	@DisplayName("성공: lastEventId가 존재하면 해당 메시지 이후의 커서 기반 미읽음 쪽지를 반환한다.")
	void findUnreadMessagesAfter_Success_WithCursor() {
		// given
		UUID receiverId = UUID.randomUUID();
		UUID lastEventId = UUID.randomUUID();
		Instant lastCreatedAt = Instant.now().minusSeconds(60);

		DirectMessage lastMessage = mock(DirectMessage.class);
		given(lastMessage.getId()).willReturn(lastEventId);
		given(lastMessage.getCreatedAt()).willReturn(lastCreatedAt);

		DirectMessage unreadMessage = mock(DirectMessage.class);
		DirectMessageDto expectedDto = mock(DirectMessageDto.class);

		given(directMessageRepository.findByIdAndReceiverId(lastEventId, receiverId))
			.willReturn(Optional.of(lastMessage));
		given(directMessageRepository.findUnreadMessagesAfter(
			eq(receiverId), eq(lastEventId), eq(lastCreatedAt), any(Pageable.class)))
			.willReturn(List.of(unreadMessage));
		given(directMessageMapper.toDto(unreadMessage)).willReturn(expectedDto);

		// when
		List<DirectMessageDto> result = directMessageRestoreService.findUnreadMessagesAfter(receiverId, lastEventId);

		// then
		assertThat(result).hasSize(1);
		assertThat(result.get(0)).isEqualTo(expectedDto);
	}

	@Test
	@DisplayName("성공: lastEventId에 해당하는 쪽지를 찾을 수 없으면 10분 이내의 전체 미읽음 쪽지를 폴백(Fallback) 조회하여 반환한다.")
	void findUnreadMessagesAfter_Fallback_WhenLastMessageNotFound() {
		// given
		UUID receiverId = UUID.randomUUID();
		UUID lastEventId = UUID.randomUUID();

		DirectMessage unreadMessage = mock(DirectMessage.class);
		DirectMessageDto expectedDto = mock(DirectMessageDto.class);

		given(directMessageRepository.findByIdAndReceiverId(lastEventId, receiverId))
			.willReturn(Optional.empty());

		given(directMessageRepository.findRecentUnreadMessages(eq(receiverId), any(Instant.class), any(Pageable.class)))
			.willReturn(List.of(unreadMessage));

		given(directMessageMapper.toDto(unreadMessage)).willReturn(expectedDto);

		// when
		List<DirectMessageDto> result = directMessageRestoreService.findUnreadMessagesAfter(receiverId, lastEventId);

		// then
		assertThat(result).hasSize(1);
		assertThat(result.get(0)).isEqualTo(expectedDto);

		verify(directMessageRepository).findRecentUnreadMessages(eq(receiverId), any(Instant.class),
			any(Pageable.class));
		verify(directMessageRepository, never()).findUnreadMessagesAfter(any(), any(), any(), any());
	}
}
