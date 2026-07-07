package com.team04.mopl.playlist.entity;

import java.time.Instant;
import java.util.Objects;

import com.team04.mopl.common.entity.BaseUpdatableEntity;
import com.team04.mopl.user.entity.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "playlists")
public class Playlist extends BaseUpdatableEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "owner_id", nullable = false)
	private User owner;

	@Column(name = "title", nullable = false, length = 100)
	private String title;

	@Column(name = "description", nullable = false, columnDefinition = "TEXT")
	private String description;

	@Column(name = "deleted_at", nullable = true)
	private Instant deletedAt;

	@Builder
	protected Playlist(User owner, String title, String description) {
		this.owner = owner;
		this.title = title;
		this.description = description;

		this.deletedAt = null;
	}

	public void update(String title, String description) {
		if (title != null) {
			this.title = title;
		}
		if (description != null) {
			this.description = description;
		}
	}

	public void touchUpdatedAt(Instant updatedAt) {
		if (updatedAt != null) {
			this.updatedAt = updatedAt;
		}
	}

	// 삭제 상태로 mark
	public void markDeleted(Instant deletedAt) {
		if (this.deletedAt == null) {
			this.deletedAt = Objects.requireNonNull(deletedAt);
		}
	}
}
