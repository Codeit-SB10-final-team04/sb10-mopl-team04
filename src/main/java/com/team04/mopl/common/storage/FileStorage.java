package com.team04.mopl.common.storage;

import org.springframework.web.multipart.MultipartFile;

// 파일 저장소 공통 인터페이스 (썸네일, 유저 프로필 등 도메인 공용)
// directory로 용도별 저장 경로를 구분한다 (예: "thumbnails", "profiles")
public interface FileStorage {

	// 파일을 저장하고 접근 가능한 URL을 반환
	String store(MultipartFile file, String directory);

	// URL에 해당하는 파일 삭제
	void delete(String fileUrl);
}
