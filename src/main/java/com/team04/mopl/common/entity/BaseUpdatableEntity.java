package com.team04.mopl.common.entity;

import java.time.Instant;

import org.springframework.data.annotation.LastModifiedDate;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.NoArgsConstructor;

@MappedSuperclass
@NoArgsConstructor
@Getter
public abstract class BaseUpdatableEntity extends BaseEntity {

	@LastModifiedDate
	@Column(name = "updated_at", nullable = false)
	protected Instant updatedAt;
}
