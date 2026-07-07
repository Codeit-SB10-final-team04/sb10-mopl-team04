package com.team04.mopl.content.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.team04.mopl.config.MapStructConfig;
import com.team04.mopl.content.dto.response.ContentDto;
import com.team04.mopl.content.entity.Content;

@Mapper(config = MapStructConfig.class)
public interface ContentMapper {

	// Content 엔티티의 watcherCount가 아닌 파라미터의 watcherCount를 사용
	@Mapping(source = "watcherCount", target = "watcherCount")
	ContentDto toDto(Content content, List<String> tags, Long watcherCount);
}
