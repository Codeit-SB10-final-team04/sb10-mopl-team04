package com.team04.mopl.content.mapper;

import java.util.List;

import org.mapstruct.Mapper;

import com.team04.mopl.config.MapStructConfig;
import com.team04.mopl.content.dto.response.ContentDto;
import com.team04.mopl.content.entity.Content;

@Mapper(config = MapStructConfig.class)
public interface ContentMapper {

	ContentDto toDto(Content content, List<String> tags, Long watcherCount);
}
