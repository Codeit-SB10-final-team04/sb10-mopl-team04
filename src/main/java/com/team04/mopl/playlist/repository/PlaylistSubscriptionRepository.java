package com.team04.mopl.playlist.repository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.team04.mopl.playlist.dto.row.PlaylistSubscriberCountRow;
import com.team04.mopl.playlist.entity.PlaylistSubscription;

public interface PlaylistSubscriptionRepository extends JpaRepository<PlaylistSubscription, UUID> {

	@Query(value = """
		SELECT new com.team04.mopl.playlist.dto.row.PlaylistSubscriberCountRow(ps.playlist.id, COUNT(ps.playlist.id))
		FROM PlaylistSubscription AS ps
		WHERE ps.playlist.id IN :playlistIds
		GROUP BY ps.playlist.id
		""")
	List<PlaylistSubscriberCountRow> countAllSubscribersByPlaylistIds(@Param("playlistIds") List<UUID> playlistIds);

	@Query(value = """
		SELECT ps.playlist.id
		FROM PlaylistSubscription AS ps
		WHERE ps.playlist.id IN :playlistIds
				AND ps.subscriber.id = :currentUserId
		""")
	Set<UUID> findSubscribedPlaylistIds(
		@Param("playlistIds") List<UUID> playlistIds,
		@Param("currentUserId") UUID currentUserId
	);

	boolean existsByPlaylistIdAndSubscriberId(UUID playlistId, UUID subscriberId);

	Optional<PlaylistSubscription> findByPlaylistIdAndSubscriberId(UUID playlistId, UUID subscriberId);

	@Query(value = """
		SELECT s.id
		FROM PlaylistSubscription AS ps
		JOIN ps.subscriber AS s
		WHERE ps.playlist.id = :playlistId
			AND s.locked = false 
		""")
	Set<UUID> findSubscriberIdsByPlaylistId(@Param("playlistId") UUID playlistId);
}
