package com.team04.mopl.content.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.team04.mopl.content.dto.response.ContentDto;
import com.team04.mopl.content.service.ContentService;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/contents")
public class ContentController implements ContentControllerDocs {

	private final ContentService contentService;

	@Override
	@GetMapping("/{contentId}")
	public ResponseEntity<ContentDto> getContent(@PathVariable UUID contentId) {

		ContentDto contentDto = contentService.getContent(contentId);

		return ResponseEntity.status(HttpStatus.OK).body(contentDto);
	}
}
