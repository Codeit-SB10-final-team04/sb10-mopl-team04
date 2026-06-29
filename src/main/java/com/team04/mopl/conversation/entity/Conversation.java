package com.team04.mopl.conversation.entity;

import com.team04.mopl.common.entity.BaseUpdatableEntity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "conversations")
public class Conversation extends BaseUpdatableEntity {

	// 생성자 대신 Conversation 객체를 생성하는 정적 메서드
	public static Conversation create() {
		return new Conversation();
	}
}
