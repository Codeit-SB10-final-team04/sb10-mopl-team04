package com.team04.mopl.notification.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.team04.mopl.notification.entity.Notification;
import com.team04.mopl.notification.repository.qdsl.NotificationQdslRepository;

public interface NotificationRepository extends JpaRepository<Notification, UUID>, NotificationQdslRepository {

	Optional<Notification> findByIdAndReceiverId(UUID notificationId, UUID receiverId);
}
