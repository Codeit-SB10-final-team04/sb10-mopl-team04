package com.team04.mopl.content.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.team04.mopl.content.dto.row.TagRow;
import com.team04.mopl.content.entity.ContentTag;

public interface ContentTagRepository extends JpaRepository<ContentTag, UUID> {

	@Query(value = """
		SELECT new com.team04.mopl.content.dto.row.TagRow(ct.content.id, ct.tag.name)
		FROM ContentTag AS ct
		LEFT JOIN ct.tag AS t
		WHERE ct.content.id IN :contentIds
		""")
	List<TagRow> findTagNamesByContentIds(@Param("contentIds") List<UUID> contentIds);
}
