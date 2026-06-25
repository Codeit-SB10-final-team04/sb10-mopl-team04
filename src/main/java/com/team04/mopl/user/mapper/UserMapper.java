package com.team04.mopl.user.mapper;

import org.mapstruct.Mapper;

import com.team04.mopl.config.MapStructConfig;
import com.team04.mopl.user.dto.response.UserDto;
import com.team04.mopl.user.entity.User;

@Mapper(config = MapStructConfig.class)
public interface UserMapper {

	UserDto toDto(User user);
}
