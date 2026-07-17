package com.team04.mopl.content.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.team04.mopl.content.dto.row.TagRow;
import com.team04.mopl.content.entity.Content;
import com.team04.mopl.content.entity.ContentTag;
import com.team04.mopl.content.entity.Tag;

public interface ContentTagRepository extends JpaRepository<ContentTag, UUID> {

	// ContentSummary의 tags 일괄 조합을 위한 조회 메서드
	@Query(value = """
		SELECT new com.team04.mopl.content.dto.row.TagRow(ct.content.id, t.name)
		FROM ContentTag AS ct
		LEFT JOIN ct.tag AS t
		WHERE ct.content.id IN :contentIds
		""")
	List<TagRow> findTagNamesByContentIds(@Param("contentIds") List<UUID> contentIds);

	// contentId로 관련 태그명들 조회하는 메서드
	@Query("SELECT t.name FROM ContentTag ct JOIN ct.tag t WHERE ct.content.id = :contentId")
	List<String> findTagNamesByContentId(@Param("contentId") UUID contentId);

	@Modifying
	@Query("DELETE FROM ContentTag ct WHERE ct.content = :content AND ct.tag.name IN :tagNames")
	void deleteByContentAndTagNameIn(@Param("content") Content content, @Param("tagNames") List<String> tagNames);

	boolean existsByContentAndTag(Content content, Tag tag);

	void deleteAllByContent(Content content);
}
