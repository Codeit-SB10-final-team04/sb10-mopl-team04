package com.team04.mopl.common.entity;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.NoArgsConstructor;

@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@NoArgsConstructor
@Getter
public abstract class BaseEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	protected UUID id;

	@CreatedDate
	@Column(name = "created_at", nullable = false, updatable = false)
	protected Instant createdAt;
}
