package com.team04.mopl.content.entity;

import com.team04.mopl.common.entity.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "tags")
@NoArgsConstructor
public class Tag extends BaseEntity {
	@Column(nullable = false, length = 100, unique = true)
	String name;

	@Builder
	public Tag(String name) {
		this.name = name;
	}
}
