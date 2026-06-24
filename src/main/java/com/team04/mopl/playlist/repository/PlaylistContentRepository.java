package com.team04.mopl.playlist.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.team04.mopl.playlist.dto.row.PlaylistContentRow;
import com.team04.mopl.playlist.entity.PlaylistContent;

public interface PlaylistContentRepository extends JpaRepository<PlaylistContent, UUID> {

	@Query(value = """
		SELECT new com.team04.mopl.playlist.dto.row.PlaylistContentRow(pc.playlist.id, c)
		FROM PlaylistContent AS pc
		LEFT JOIN pc.content AS c
		WHERE pc.playlist.id IN :playlistIds
		""")
	List<PlaylistContentRow> findAllContentsByPlaylistIds(@Param("playlistIds") List<UUID> playlistIds);
}
