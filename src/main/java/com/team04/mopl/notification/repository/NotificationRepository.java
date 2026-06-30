package com.team04.mopl.notification.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.team04.mopl.notification.entity.Notification;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
}
