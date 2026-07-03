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

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContentChatService {

	private final UserRepository userRepository;

	public ContentChatDto createChatMessage(Principal principal, ContentChatSendRequest request) {
		UUID userId = getUserId(principal);

		User user = userRepository.findByIdAndLockedFalse(userId)
			.orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND)
				.addDetail("userId", userId));

		UserSummary sender = new UserSummary(
			user.getId(),
			user.getName(),
			user.getProfileImageUrl()
		);

		return new ContentChatDto(sender, request.content());
	}

	// Principal에서 userId 추출
	private UUID getUserId(Principal principal) {
		UsernamePasswordAuthenticationToken auth = (UsernamePasswordAuthenticationToken)principal;
		MoplUserDetails userDetails = (MoplUserDetails)auth.getPrincipal();
		return userDetails.getUserId();
	}
}
