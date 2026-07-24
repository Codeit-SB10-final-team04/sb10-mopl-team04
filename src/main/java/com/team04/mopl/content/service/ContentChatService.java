package com.team04.mopl.content.service;

import java.security.Principal;
import java.util.UUID;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team04.mopl.auth.security.MoplUserDetails;
import com.team04.mopl.common.dto.UserSummary;
import com.team04.mopl.content.dto.request.ContentChatSendRequest;
import com.team04.mopl.content.dto.response.ContentChatDto;
import com.team04.mopl.user.entity.User;
import com.team04.mopl.user.exception.UserErrorCode;
import com.team04.mopl.user.exception.UserException;
import com.team04.mopl.user.repository.UserRepository;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContentChatService {

	private final UserRepository userRepository;
	private final MeterRegistry meterRegistry;

	public ContentChatDto createChatMessage(Principal principal, ContentChatSendRequest request) {
		// 커스텀 메트릭: 채팅 메시지 생성 처리 시간 측정 시작
		Timer.Sample sample = Timer.start(meterRegistry);

		UUID userId = getUserId(principal);

		log.debug("[CONTENT_CHAT_CREATE] 채팅 메시지 생성 시작: userId={}", userId);

		try {
			User user = userRepository.findByIdAndLockedFalse(userId)
				.orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND)
					.addDetail("userId", userId));

			UserSummary sender = new UserSummary(
				user.getId(),
				user.getName(),
				user.getProfileImageUrl()
			);

			ContentChatDto responseDto = new ContentChatDto(sender, request.content());

			// 커스텀 메트릭: Content 채팅 생성 성공 처리 시간
			sample.stop(meterRegistry.timer(
				"mopl.content.chat.send.duration",
				"result", "success"
			));

			// 커스텀 메트릭: Content 채팅 생성 성공
			meterRegistry.counter(
				"mopl.content.chat.send",
				"result", "success"
			).increment();

			return responseDto;

		} catch (Exception e) {
			// 커스텀 메트릭: Content 채팅 생성 실패 처리 시간
			sample.stop(meterRegistry.timer(
				"mopl.content.chat.send.duration",
				"result", "failure"
			));

			// 커스텀 메트릭: Content 채팅 생성 실패
			meterRegistry.counter(
				"mopl.content.chat.send",
				"result", "failure"
			).increment();

			throw e;
		}
	}

	// Principal에서 userId 추출
	private UUID getUserId(Principal principal) {
		UsernamePasswordAuthenticationToken auth = (UsernamePasswordAuthenticationToken)principal;
		MoplUserDetails userDetails = (MoplUserDetails)auth.getPrincipal();
		return userDetails.getUserId();
	}
}
