package com.team04.mopl.content.storage;

import org.springframework.web.multipart.MultipartFile;

public interface ThumbnailStorage {
	String store(MultipartFile thumbnail);

	void delete(String thumbnail);
}
