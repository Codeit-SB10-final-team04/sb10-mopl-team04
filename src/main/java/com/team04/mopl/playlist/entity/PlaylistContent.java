package com.team04.mopl.playlist.entity;

import com.team04.mopl.common.entity.BaseEntity;
import com.team04.mopl.content.entity.Content;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Table(
	name = "playlist_contents",
	uniqueConstraints = {
		@UniqueConstraint(
			name = "uq_playlist_contents",
			columnNames = {"playlist_id", "content_id"}
		)
	}
)
public class PlaylistContent extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "playlist_id", nullable = false)
	private Playlist playlist;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "content_id", nullable = false)
	private Content content;

	public PlaylistContent(Playlist playlist, Content content) {
		this.playlist = playlist;
		this.content = content;
	}
}
