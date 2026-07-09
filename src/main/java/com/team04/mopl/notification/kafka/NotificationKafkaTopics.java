package com.team04.mopl.notification.kafka;

// topic 상수만 모아두는 클래스
public final class NotificationKafkaTopics {

	public static final String PLAYLIST_SUBSCRIBED = "mopl.playlist.subscribed";
	public static final String PLAYLIST_CONTENT_ADDED = "mopl.playlist.content.added";
	public static final String FOLLOW_CREATED = "mopl.follow.created";
	public static final String PLAYLIST_CREATED = "mopl.playlist.created";
	public static final String USER_ROLE_CHANGED = "mopl.user.role.changed";

	private NotificationKafkaTopics() {
	}
}
