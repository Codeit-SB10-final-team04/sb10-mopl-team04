package com.team04.mopl.content.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import com.team04.mopl.common.dto.CursorPageResponse;
import com.team04.mopl.content.dto.request.ContentCreateRequest;
import com.team04.mopl.content.dto.response.ContentDto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Content API", description = "콘텐츠 관리")
public interface ContentControllerDocs {
	@Operation(summary = "콘텐트 단건 조회")
	ResponseEntity<ContentDto> getContent(UUID contentId);

	@Operation(summary = "[어드민] 콘텐츠 생성")
	ResponseEntity<ContentDto> createContent(ContentCreateRequest contentCreateRequest, MultipartFile thumbnail);

	@Operation(summary = "콘텐츠 목록 조회")
	ResponseEntity<CursorPageResponse<ContentDto>> getContents();
}
