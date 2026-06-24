package com.team04.mopl.content.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.team04.mopl.content.entity.Content;
import com.team04.mopl.content.entity.ContentType;

public interface ContentRepository extends JpaRepository<Content, UUID> {

	/**
	 * 동일한 title + type 조합의 Content 존재 여부 확인
	 * 배치 수집 시 중복 저장 방지에 사용
	 */
	boolean existsByTitleAndType(String title, ContentType type);
}
