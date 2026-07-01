package com.team04.mopl.review.mapper;

import javax.annotation.processing.Generated;

import org.mapstruct.Mapper;

import com.team04.mopl.common.dto.UserSummary;
import com.team04.mopl.config.MapStructConfig;
import com.team04.mopl.review.dto.response.ReviewDto;
import com.team04.mopl.review.entity.Review;

@Mapper(config = MapStructConfig.class)
@Generated("jacoco-exclude")
public interface ReviewMapper {

	ReviewDto toDto(Review review, UserSummary author);
}
