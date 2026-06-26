package com.team04.mopl.playlist.batch;

import static org.mockito.Mockito.*;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;

import com.team04.mopl.playlist.repository.PlaylistRepository;

@ExtendWith(MockitoExtension.class)
class PlaylistHardDeleteJobConfigTest {

	@Mock
	private PlaylistRepository playlistRepository;

	@InjectMocks
	private PlaylistHardDeleteJobConfig playlistHardDeleteJobConfig;

	@Test
	@DisplayName("Writer는 Chunk로 전달 받은 플레이리스트 id 목록으로 플레이리스트를 물리 삭제한다.")
	void playlistHardDeleteItemWriter_deletePlaylist_whenChunkReceived() throws Exception {
		// given
		UUID playlistId1 = UUID.randomUUID();
		UUID playlistId2 = UUID.randomUUID();

		ItemWriter<UUID> writer = playlistHardDeleteJobConfig.playlistHardDeleteItemWriter();
		Chunk<UUID> chunk = new Chunk<>(playlistId1, playlistId2);

		// when
		writer.write(chunk);

		// then
		verify(playlistRepository).deleteAllByPlaylistIds(List.of(playlistId1, playlistId2));
	}
}