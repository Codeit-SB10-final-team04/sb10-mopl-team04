package com.team04.mopl.notification.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.team04.mopl.notification.entity.Notification;
import com.team04.mopl.notification.repository.qdsl.NotificationQdslRepository;

public interface NotificationRepository extends JpaRepository<Notification, UUID>, NotificationQdslRepository {

	Optional<Notification> findByIdAndReceiverId(UUID notificationId, UUID receiverId);

	@Query(value = """
		SELECT n 
		FROM Notification AS n
		WHERE n.receiver.id = :receiverId
			AND n.readAt IS NULL 
			AND (n.createdAt > :lastNotificationCreatedAt
					OR (n.createdAt = :lastNotificationCreatedAt
							AND n.id > :lastNotificationId
					)
			)
		ORDER BY n.createdAt ASC, n.id ASC
		""")
	List<Notification> findUnreadNotificationsAfter(
		@Param("receiverId") UUID receiverId,
		@Param("lastNotificationId") UUID lastNotificationId,
		@Param("lastNotificationCreatedAt") Instant lastNotificationCreatedAt,
		Pageable pageable
	);

}
