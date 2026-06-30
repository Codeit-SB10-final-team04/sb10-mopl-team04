package com.team04.mopl.content.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.team04.mopl.common.dto.CursorResponse;
import com.team04.mopl.content.dto.request.ContentCreateRequest;
import com.team04.mopl.content.dto.request.ContentPageRequest;
import com.team04.mopl.content.dto.request.ContentUpdateRequest;
import com.team04.mopl.content.dto.response.ContentDto;
import com.team04.mopl.content.service.ContentService;

import jakarta.validation.Valid;
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

	@Override
	@PreAuthorize("hasRole('ADMIN')")
	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<ContentDto> createContent(
		@Valid @RequestPart("request") ContentCreateRequest contentCreateRequest,
		@RequestPart MultipartFile thumbnail // thumbnail은 필수
	) {

		ContentDto contentDto = contentService.createContent(contentCreateRequest, thumbnail);

		return ResponseEntity.status(HttpStatus.CREATED).body(contentDto);
	}

	@Override
	@GetMapping
	public ResponseEntity<CursorResponse<ContentDto>> getContents(@Valid ContentPageRequest contentPageRequest) {

		CursorResponse<ContentDto> contents = contentService.getContents(contentPageRequest);

		return ResponseEntity.status(HttpStatus.OK).body(contents);
	}

	@Override
	@PreAuthorize("hasRole('ADMIN')")
	@PatchMapping(value = "/{contentId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	public ResponseEntity<ContentDto> updateContent(@PathVariable UUID contentId,
		@Valid @RequestPart("request") ContentUpdateRequest contentUpdateRequest,
		@RequestPart(required = false) MultipartFile thumbnail
	) {

		ContentDto contentDto = contentService.updateContent(contentId, contentUpdateRequest, thumbnail);

		return ResponseEntity.status(HttpStatus.OK).body(contentDto);
	}
}
