package com.team04.mopl.notification.batch;

import static org.mockito.Mockito.*;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;

import com.team04.mopl.notification.repository.NotificationRepository;

@ExtendWith(MockitoExtension.class)
class NotificationHardDeleteJobConfigTest {

	@Mock
	private NotificationRepository notificationRepository;

	@InjectMocks
	private NotificationHardDeleteJobConfig notificationHardDeleteJobConfig;

	@Test
	@DisplayName("Writer는 Chunk로 전달 받은 알림 id 목록으로 알림을 물리 삭제한다.")
	void notificationHardDeleteItemWriter_deleteNotification_whenChunkReceived() throws Exception {
		// given
		UUID notificationId1 = UUID.randomUUID();
		UUID notificationId2 = UUID.randomUUID();

		ItemWriter<UUID> writer = notificationHardDeleteJobConfig.notificationHardDeleteItemWriter();
		Chunk<UUID> chunk = new Chunk<>(notificationId1, notificationId2);

		// when
		writer.write(chunk);

		// then
		verify(notificationRepository).deleteAllByNotificationIds(List.of(notificationId1, notificationId2));
	}

	@Test
	@DisplayName("Writer는 Chunk로 전달 받은 알림 id 목록이 비었다면 알림 삭제 메서드가 동작하지 않는다.")
	void notificationHardDeleteItemWriter_NotDeleteNotification_whenEmptyChunkReceived() throws Exception {
		// given
		ItemWriter<UUID> writer = notificationHardDeleteJobConfig.notificationHardDeleteItemWriter();
		Chunk<UUID> chunk = new Chunk<>();

		// when
		writer.write(chunk);

		// then
		verify(notificationRepository, never()).deleteAllByNotificationIds(anyList());
	}
}