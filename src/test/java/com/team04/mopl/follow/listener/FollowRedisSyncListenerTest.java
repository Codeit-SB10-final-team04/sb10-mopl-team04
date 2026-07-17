package com.team04.mopl.follow.listener;

import static org.mockito.Mockito.*;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.team04.mopl.follow.event.FollowCreatedEvent;
import com.team04.mopl.follow.event.FollowDeletedEvent;

@ExtendWith(MockitoExtension.class)
class FollowRedisSyncListenerTest {

	@Mock
	private FollowRedisSyncProcessor followRedisSyncProcessor;

	@InjectMocks
	private FollowRedisSyncListener followRedisSyncListener;

	@Test
	@DisplayName("성공: 팔로우 생성 이벤트가 수신되면 Processor의 syncRedisOnFollowCreated 메서드로 위임한다.")
	void onFollowCreated_DelegatesToProcessor_Success() {
		// given
		FollowCreatedEvent event = FollowCreatedEvent.of(
			UUID.randomUUID(),
			"팔로위",
			UUID.randomUUID(),
			"팔로워"
		);

		// when
		followRedisSyncListener.onFollowCreated(event);

		// then
		verify(followRedisSyncProcessor, times(1)).syncRedisOnFollowCreated(event);
	}

	@Test
	@DisplayName("성공: 팔로우 취소 이벤트가 수신되면 Processor의 syncRedisOnFollowDeleted 메서드로 위임한다.")
	void onFollowDeleted_DelegatesToProcessor_Success() {
		// given
		FollowDeletedEvent event = new FollowDeletedEvent(
			UUID.randomUUID(),
			UUID.randomUUID()
		);

		// when
		followRedisSyncListener.onFollowDeleted(event);

		// then
		verify(followRedisSyncProcessor, times(1)).syncRedisOnFollowDeleted(event);
	}
}