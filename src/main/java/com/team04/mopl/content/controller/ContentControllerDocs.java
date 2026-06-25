package com.team04.mopl.content.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;

import com.team04.mopl.content.dto.response.ContentDto;

import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Content API", description = "콘텐츠 관리")
public interface ContentControllerDocs {
	ResponseEntity<ContentDto> getContent(UUID contentId);
}
