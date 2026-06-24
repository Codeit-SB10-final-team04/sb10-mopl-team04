package com.team04.mopl.playlist.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.team04.mopl.config.MapStructConfig;
import com.team04.mopl.content.dto.response.ContentSummary;
import com.team04.mopl.playlist.dto.response.PlaylistDto;
import com.team04.mopl.playlist.entity.Playlist;
import com.team04.mopl.user.dto.response.UserSummary;

@Mapper(config = MapStructConfig.class)
public interface PlaylistMapper {

	@Mapping(target = "id", source = "playlist.id")
	@Mapping(target = "owner", source = "owner")
	@Mapping(target = "title", source = "playlist.title")
	@Mapping(target = "description", source = "playlist.description")
	@Mapping(target = "updatedAt", source = "playlist.updatedAt")
	@Mapping(target = "subscriberCount", source = "subscriberCount")
	@Mapping(target = "subscribedByMe", source = "subscribedByMe")
	@Mapping(target = "contents", source = "contents")
	PlaylistDto toDto(
		Playlist playlist,
		UserSummary owner,
		long subscriberCount,
		boolean subscribedByMe,
		List<ContentSummary> contents
	);
}
