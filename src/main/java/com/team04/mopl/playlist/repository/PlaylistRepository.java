package com.team04.mopl.playlist.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.team04.mopl.playlist.entity.Playlist;

public interface PlaylistRepository extends JpaRepository<Playlist, UUID> {

	@Query(value = """
		SELECT p FROM Playlist AS p
		LEFT JOIN FETCH p.owner
		WHERE p.id = :playlistId
			AND p.deletedAt IS NULL
		""")
	Optional<Playlist> findByIdWithOwnerAndDeletedAtIsNull(@Param("playlistId") UUID playlistId);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
		DELETE FROM Playlist AS p
		WHERE p.id IN :playlistIds
		""")
	void deleteAllByPlaylistIds(@Param("playlistIds") List<UUID> playlistIds);
}
