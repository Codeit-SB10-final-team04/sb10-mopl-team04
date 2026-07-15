package com.team04.mopl.conversation.document;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.DateFormat;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.annotations.Setting;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Document(indexName = "conversations")
@Setting(settingPath = "elasticsearch/conversations-settings.json")
public class ConversationDocument {

	@Id
	private UUID id;

	// 대화 참여자들의 ID 리스트
	@Field(type = FieldType.Keyword)
	private List<UUID> participantIds;

	// 대화 참여자 닉네임 리스트
	@Field(type = FieldType.Text, analyzer = "nori")
	private List<String> participantNames;

	// 대화방 내 메시지 리스트
	@Field(type = FieldType.Text, analyzer = "nori")
	private List<String> messageContents;

	// 대화방 생성 시간
	@Field(type = FieldType.Date, format = DateFormat.date_time)
	private Instant createdAt;
}
