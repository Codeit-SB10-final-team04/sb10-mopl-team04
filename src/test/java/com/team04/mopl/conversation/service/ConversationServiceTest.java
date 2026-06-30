package com.team04.mopl.conversation.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.team04.mopl.auth.security.MoplUserDetails;
import com.team04.mopl.common.dto.UserSummary;
import com.team04.mopl.conversation.dto.request.ConversationCreateRequest;
import com.team04.mopl.conversation.dto.response.ConversationDto;
import com.team04.mopl.conversation.entity.Conversation;
import com.team04.mopl.conversation.entity.ConversationParticipant;
import com.team04.mopl.conversation.exception.ConversationErrorCode;
import com.team04.mopl.conversation.exception.ConversationException;
import com.team04.mopl.conversation.mapper.ConversationMapper;
import com.team04.mopl.conversation.mapper.ConversationParticipantMapper;
import com.team04.mopl.conversation.repository.ConversationParticipantRepository;
import com.team04.mopl.conversation.repository.ConversationRepository;
import com.team04.mopl.user.entity.User;
import com.team04.mopl.user.entity.UserRole;
import com.team04.mopl.user.exception.UserErrorCode;
import com.team04.mopl.user.exception.UserException;
import com.team04.mopl.user.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

	@InjectMocks
	private ConversationService conversationService;

	@Mock
	private ConversationRepository conversationRepository;

	@Mock
	private ConversationParticipantRepository conversationParticipantRepository;

	@Mock
	private UserRepository userRepository;

	@Mock
	private ConversationMapper conversationMapper;

	@Mock
	private ConversationParticipantMapper conversationParticipantMapper;

	/*
	=========================
		대화 생성
	=========================
	 */
	@Test
	@DisplayName("성공: 유효한 요청일 경우 대화방을 생성하고 DTO를 반환한다.")
	void createConversation_Success() {
		// given
		UUID requestUserId = UUID.randomUUID();
		MoplUserDetails userDetails = MoplUserDetails.authenticated(
			requestUserId,
			"test@test.com",
			UserRole.USER);
		User requestUser = mock(User.class);
		given(requestUser.getId()).willReturn(requestUserId);

		UUID withUserId = UUID.randomUUID();
		ConversationCreateRequest request = new ConversationCreateRequest(withUserId);
		User withUser = mock(User.class);
		given(withUser.getId()).willReturn(withUserId);

		given(userRepository.findById(requestUserId)).willReturn(Optional.of(requestUser));
		given(userRepository.findById(withUserId)).willReturn(Optional.of(withUser));

		// 요청자와 대화 참여자 간의 대화방 미존재
		given(conversationParticipantRepository.findExistingConversationId(requestUserId, withUserId))
			.willReturn(Optional.empty());

		// 대화 참여자 생성
		given(conversationParticipantMapper.toEntity(any(Conversation.class), any(User.class)))
			.willReturn(mock(ConversationParticipant.class));

		ConversationDto expectedDto = mock(ConversationDto.class);
		given(conversationMapper.toDto(any(Conversation.class), any(UserSummary.class), isNull(), eq(false)))
			.willReturn(expectedDto);

		// when
		ConversationDto result = conversationService.createConversation(request, userDetails);

		// then
		assertThat(result).isNotNull();
		verify(conversationRepository).save(any(Conversation.class));
		verify(conversationParticipantRepository).saveAll(anyList());
	}

	@Test
	@DisplayName("실패: 대상 사용자가 존재하지 않으면 예외가 발생한다.")
	void createConversation_UserNotFound_Fail() {
		// given
		UUID requestUserId = UUID.randomUUID();
		MoplUserDetails userDetails = MoplUserDetails.authenticated(
			requestUserId,
			"test@test.com",
			UserRole.USER
		);
		User requestUser = mock(User.class);

		UUID withUserId = UUID.randomUUID();
		ConversationCreateRequest request = new ConversationCreateRequest(withUserId);

		given(userRepository.findById(requestUserId)).willReturn(Optional.of(requestUser));
		// 대화 참여자 미존재
		given(userRepository.findById(withUserId)).willReturn(Optional.empty());

		// when & then
		assertThatThrownBy(() -> conversationService.createConversation(request, userDetails))
			.isInstanceOf(UserException.class)
			.hasMessageContaining(UserErrorCode.USER_NOT_FOUND.getMessage());
	}

	@Test
	@DisplayName("실패: 이미 존재하는 대화방일 경우 중복 예외가 발생한다.")
	void createConversation_DuplicateConversation_Fail() {
		// given
		UUID requestUserId = UUID.randomUUID();
		MoplUserDetails userDetails = MoplUserDetails.authenticated(
			requestUserId,
			"test@test.com",
			UserRole.USER
		);
		User requestUser = mock(User.class);
		given(requestUser.getId()).willReturn(requestUserId);

		UUID withUserId = UUID.randomUUID();
		ConversationCreateRequest request = new ConversationCreateRequest(withUserId);
		User withUser = mock(User.class);
		given(withUser.getId()).willReturn(withUserId);

		UUID existingConversationId = UUID.randomUUID();

		given(userRepository.findById(requestUserId)).willReturn(Optional.of(requestUser));
		given(userRepository.findById(withUserId)).willReturn(Optional.of(withUser));

		// 요청자와 대화 참여자 간의 대화방 존재
		given(conversationParticipantRepository.findExistingConversationId(requestUserId, withUserId))
			.willReturn(Optional.of(existingConversationId));

		// when & then
		assertThatThrownBy(() -> conversationService.createConversation(request, userDetails))
			.isInstanceOf(ConversationException.class)
			.hasMessage(ConversationErrorCode.CONVERSATION_ALREADY_EXISTS.getMessage());
	}

	@Test
	@DisplayName("실패: 저장 시 동시성 이슈(DB 제약조건 위반) 발생 시 중복 예외로 변환하여 발생시킨다.")
	void createConversation_ConcurrencyIssue_Fail() {
		// given
		UUID requestUserId = UUID.randomUUID();
		MoplUserDetails userDetails = MoplUserDetails.authenticated(
			requestUserId,
			"test@test.com",
			UserRole.USER
		);
		User requestUser = mock(User.class);
		given(requestUser.getId()).willReturn(requestUserId);

		UUID withUserId = UUID.randomUUID();
		ConversationCreateRequest request = new ConversationCreateRequest(withUserId);
		User withUser = mock(User.class);
		given(withUser.getId()).willReturn(withUserId);

		given(userRepository.findById(requestUserId)).willReturn(Optional.of(requestUser));
		given(userRepository.findById(withUserId)).willReturn(Optional.of(withUser));

		given(conversationParticipantRepository.findExistingConversationId(requestUserId, withUserId))
			.willReturn(Optional.empty());

		// 중복 요청으로 인한 DataIntegrityViolationException 발생
		willThrow(DataIntegrityViolationException.class)
			.given(conversationRepository).save(any(Conversation.class));

		// when & then
		assertThatThrownBy(() -> conversationService.createConversation(request, userDetails))
			.isInstanceOf(ConversationException.class)
			.hasMessage(ConversationErrorCode.CONVERSATION_ALREADY_EXISTS.getMessage());
	}
}