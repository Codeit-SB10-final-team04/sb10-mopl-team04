package com.team04.mopl.notification.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.team04.mopl.config.MapStructConfig;
import com.team04.mopl.notification.dto.response.NotificationDto;
import com.team04.mopl.notification.entity.Notification;

@Mapper(config = MapStructConfig.class)
public interface NotificationMapper {

	@Mapping(target = "id", source = "notification.id")
	@Mapping(target = "createdAt", source = "notification.createdAt")
	@Mapping(target = "receiverId", source = "notification.receiver.id")
	@Mapping(target = "title", source = "notification.title")
	@Mapping(target = "content", source = "notification.content")
	@Mapping(target = "level", source = "notification.level")
	NotificationDto toDto(Notification notification);
}
