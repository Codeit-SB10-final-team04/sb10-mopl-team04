package com.team04.mopl.user.repository.qdsl;

import com.team04.mopl.user.dto.request.UserPageRequest;
import com.team04.mopl.user.dto.response.UserCursorPage;

public interface UserQdslRepository {

	// 관리자 사용자 목록 커서 페이지 조회
	UserCursorPage findUsers(UserPageRequest request);
}
