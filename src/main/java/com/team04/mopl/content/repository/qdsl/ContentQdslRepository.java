package com.team04.mopl.content.repository.qdsl;

import java.util.List;

import com.team04.mopl.content.dto.request.ContentPageRequest;
import com.team04.mopl.content.entity.Content;

public interface ContentQdslRepository {
	List<Content> findContents(ContentPageRequest contentPageRequest);

	long countContents(ContentPageRequest contentPageRequest);
}
