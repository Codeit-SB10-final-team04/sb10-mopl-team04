package com.team04.mopl.playlist.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.team04.mopl.playlist.entity.Playlist;

public interface PlaylistRepository extends JpaRepository<Playlist, UUID> {
}
