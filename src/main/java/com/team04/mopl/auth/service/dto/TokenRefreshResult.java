package com.team04.mopl.auth.service.dto;

import com.team04.mopl.auth.dto.response.JwtDto;

// 서비스가 컨트롤러에게 전달하는 내부 결과 dto
public record TokenRefreshResult(
	JwtDto jwtDto,
	String refreshToken
) {
}
