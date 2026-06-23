package com.team04.mopl.playlist.entity;

import com.team04.mopl.common.entity.BaseEntity;
import com.team04.mopl.user.entity.User;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor
@Getter
@Table(
	name = "playlist_subscriptions",
	uniqueConstraints = {
		@UniqueConstraint(
			name = "uq_playlist_subscriptions",
			columnNames = {"subscriber_id", "playlist_id"}
		)
	}
)
public class PlaylistSubscription extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "subscriber_id", nullable = false)
	private User subscriber;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "playlist_id", nullable = false)
	private Playlist playlist;

	public PlaylistSubscription(User subscriber, Playlist playlist) {
		this.subscriber = subscriber;
		this.playlist = playlist;
	}
}
