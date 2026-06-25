package com.team04.mopl.playlist.dto.row;

import java.util.UUID;

import com.team04.mopl.content.entity.Content;

// 플레이리스트 id별 콘텐츠를 일괄 조회하기 위한 Repository 조회용 DTO
public record PlaylistContentRow(

	UUID playlistId,
	Content content
) {
}
