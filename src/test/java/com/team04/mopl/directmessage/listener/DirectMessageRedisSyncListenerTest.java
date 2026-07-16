package com.team04.mopl.directmessage.listener;

import static org.mockito.Mockito.*;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.team04.mopl.directmessage.dto.response.DirectMessageDto;
import com.team04.mopl.directmessage.event.DirectMessageCreatedEvent;
import com.team04.mopl.directmessage.event.DirectMessageReadEvent;

@ExtendWith(MockitoExtension.class)
class DirectMessageRedisSyncListenerTest {

	@Mock
	private DirectMessageRedisSyncProcessor directMessageRedisSyncProcessor;

	@InjectMocks
	private DirectMessageRedisSyncListener directMessageRedisSyncListener;

	@Test
	@DisplayName("성공: DM 생성 이벤트가 수신되면 Processor의 syncRedisOnDirectMessageCreated 메서드로 위임한다.")
	void onDirectMessageCreated_DelegatesToProcessor_Success() {
		// given
		DirectMessageDto mockDto = mock(DirectMessageDto.class);
		DirectMessageCreatedEvent event = new DirectMessageCreatedEvent(
			UUID.randomUUID(),
			UUID.randomUUID(),
			mockDto
		);

		// when
		directMessageRedisSyncListener.onDirectMessageCreated(event);

		// then
		verify(directMessageRedisSyncProcessor, times(1)).syncRedisOnDirectMessageCreated(event);
	}

	@Test
	@DisplayName("성공: DM 읽음 처리 이벤트가 수신되면 Processor의 syncRedisOnDirectMessageRead 메서드로 위임한다.")
	void handleDirectMessageReadForRedis_DelegatesToProcessor_Success() {
		// given
		DirectMessageReadEvent event = new DirectMessageReadEvent(
			UUID.randomUUID(),
			UUID.randomUUID(),
			UUID.randomUUID()
		);

		// when
		directMessageRedisSyncListener.handleDirectMessageReadForRedis(event);

		// then
		verify(directMessageRedisSyncProcessor, times(1)).syncRedisOnDirectMessageRead(event);
	}
}