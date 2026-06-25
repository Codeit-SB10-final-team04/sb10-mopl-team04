package com.team04.mopl.follow.mapper;

import org.mapstruct.Mapper;

import com.team04.mopl.config.MapStructConfig;
import com.team04.mopl.follow.dto.response.FollowDto;
import com.team04.mopl.follow.entity.Follow;
import com.team04.mopl.user.entity.User;

@Mapper(config = MapStructConfig.class)
public interface FollowMapper {

	// // FollowRequest -> Follow Entity
	// @Mapping(target = "followee", source = "followee")
	// @Mapping(target = "follower", source = "follower")
	Follow toEntity(User followee, User follower);

	// // Follow Entity -> FollowDto
	// @Mapping(target = "id", source = "follow.id")
	// @Mapping(target = "followeeId", source = "follow.followee.id")
	// @Mapping(target = "followerId", source = "follow.follower.id")
	FollowDto toDto(Follow follow);
}
