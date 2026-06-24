package com.team04.mopl.playlist.mapper;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;

import com.team04.mopl.content.dto.response.ContentSummary;
import com.team04.mopl.content.entity.ContentType;
import com.team04.mopl.playlist.dto.response.PlaylistDto;
import com.team04.mopl.playlist.entity.Playlist;
import com.team04.mopl.user.dto.response.UserSummary;
import com.team04.mopl.user.entity.User;

class PlaylistMapperTest {

	private final PlaylistMapper playlistMapper = Mappers.getMapper(PlaylistMapper.class);

	@Test
	@DisplayName("플레이리스트와 응답 조립 값을 PlaylistDto로 매핑")
	void toDto_returnPlaylistDto_whenValidSources() {
		// given
		UUID ownerId = UUID.randomUUID();
		UUID playlistId = UUID.randomUUID();
		Instant updatedAt = Instant.parse("2026-06-24T01:00:00Z");

		User owner = User.builder()
			.name("테스트 사용자")
			.email("test@gmail.com")
			.profileImageUrl("https://example.com")
			.build();
		ReflectionTestUtils.setField(owner, "id", ownerId);

		Playlist playlist = Playlist.builder()
			.owner(owner)
			.title("테스트 제목")
			.description("테스트 설명")
			.build();
		ReflectionTestUtils.setField(playlist, "id", playlistId);

		UserSummary ownerSummary = new UserSummary(ownerId, owner.getName(), owner.getProfileImageUrl());
		ContentSummary contentSummary = new ContentSummary(
			UUID.randomUUID(),
			ContentType.movie,
			"콘텐츠 제목",
			"콘텐츠 설명",
			"https://thumbnail.url",
			List.of("액션", "드라마"),
			BigDecimal.valueOf(4.3),
			10L
		);
		List<ContentSummary> contents = List.of(contentSummary);

		// when
		PlaylistDto result = playlistMapper.toDto(
			playlist,
			ownerSummary,
			5L,
			true,
			contents
		);

		// then
		assertEquals(playlist.getId(), result.id());
		assertEquals(ownerSummary, result.owner());
		assertEquals(playlist.getTitle(), result.title());
		assertEquals(playlist.getDescription(), result.description());
		assertEquals(5L, result.subscriberCount());
		assertTrue(result.subscribedByMe());
		assertEquals(List.of(contentSummary), result.contents());
	}
}