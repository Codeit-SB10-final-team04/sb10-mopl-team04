package com.team04.mopl.user.storage;

import org.springframework.web.multipart.MultipartFile;

public interface ProfileImageStorage {
	String store(MultipartFile profileImage);

	void delete(String profileImageUrl);
}
