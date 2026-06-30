package com.team04.mopl.conversation.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.*;

import java.util.List;
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
import com.team04.mopl.directmessage.dto.response.DirectMessageDto;
import com.team04.mopl.directmessage.entity.DirectMessage;
import com.team04.mopl.directmessage.mapper.DirectMessageMapper;
import com.team04.mopl.directmessage.repository.DirectMessageRepository;
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
	private DirectMessageRepository directMessageRepository;

	@Mock
	private UserRepository userRepository;

	@Mock
	private ConversationMapper conversationMapper;

	@Mock
	private ConversationParticipantMapper conversationParticipantMapper;

	@Mock
	private DirectMessageMapper directMessageMapper;

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

	@Test
	@DisplayName("성공: 메시지가 존재하는 대화방을 조회하면 상대방 정보와 마지막 메시지를 조립하여 반환한다.")
	void findConversationById_WithLatestMessage_Success() {
		// given
		UUID requestUserId = UUID.randomUUID();
		User requestUser = mock(User.class);
		given(requestUser.getId()).willReturn(requestUserId);

		MoplUserDetails moplUserDetails = mock(MoplUserDetails.class);
		given(moplUserDetails.getUserId()).willReturn(requestUserId);

		UUID withUserId = UUID.randomUUID();
		User withUser = mock(User.class);
		given(withUser.getId()).willReturn(withUserId);

		UUID conversationId = UUID.randomUUID();
		Conversation conversation = mock(Conversation.class);
		given(conversation.getId()).willReturn(conversationId);

		ConversationParticipant participant1 = mock(ConversationParticipant.class);
		ConversationParticipant participant2 = mock(ConversationParticipant.class);

		DirectMessage latestMessage = mock(DirectMessage.class);
		DirectMessageDto latestMessageDto = mock(DirectMessageDto.class);

		given(conversationRepository.findById(conversationId)).willReturn(Optional.of(conversation));
		given(conversationParticipantRepository.findByConversationId(conversationId)).willReturn(
			List.of(participant1, participant2));
		given(participant1.getUser()).willReturn(requestUser);
		given(participant2.getUser()).willReturn(withUser);

		given(directMessageRepository.findTopByConversationIdOrderByCreatedAtDesc(conversationId)).willReturn(
			Optional.of(latestMessage));
		given(directMessageMapper.toDto(latestMessage)).willReturn(latestMessageDto);

		// 마지막 메시지 상태: 안 읽음
		given(latestMessage.getReceiver()).willReturn(requestUser);
		given(latestMessage.isRead()).willReturn(false);

		ConversationDto expectedDto = mock(ConversationDto.class);
		given(conversationMapper.toDto(eq(conversation), any(UserSummary.class), eq(latestMessageDto), eq(true)))
			.willReturn(expectedDto);

		// when
		ConversationDto result = conversationService.findConversationById(conversationId, moplUserDetails);

		// then
		assertThat(result).isEqualTo(expectedDto);
	}

	@Test
	@DisplayName("성공: 메시지가 없는 신규 대화방을 조회하면 마지막 메시지는 null, 안읽음 여부는 false로 반환한다.")
	void findConversationById_NoMessage_Success() {
		// given
		UUID requestUserId = UUID.randomUUID();
		User requestUser = mock(User.class);
		given(requestUser.getId()).willReturn(requestUserId);

		MoplUserDetails moplUserDetails = mock(MoplUserDetails.class);
		given(moplUserDetails.getUserId()).willReturn(requestUserId);

		UUID withUserId = UUID.randomUUID();
		User withUser = mock(User.class);
		given(withUser.getId()).willReturn(withUserId);

		UUID conversationId = UUID.randomUUID();
		Conversation conversation = mock(Conversation.class);
		given(conversation.getId()).willReturn(conversationId);

		ConversationParticipant participant1 = mock(ConversationParticipant.class);
		ConversationParticipant participant2 = mock(ConversationParticipant.class);

		given(conversationRepository.findById(conversationId)).willReturn(Optional.of(conversation));
		given(conversationParticipantRepository.findByConversationId(conversationId)).willReturn(
			List.of(participant1, participant2));
		given(participant1.getUser()).willReturn(requestUser);
		given(participant2.getUser()).willReturn(withUser);

		// 마지막 메시지 없음
		given(directMessageRepository.findTopByConversationIdOrderByCreatedAtDesc(conversationId)).willReturn(
			Optional.empty());

		ConversationDto expectedDto = mock(ConversationDto.class);
		given(conversationMapper.toDto(eq(conversation), any(UserSummary.class), eq(null), eq(false)))
			.willReturn(expectedDto);

		// when
		ConversationDto result = conversationService.findConversationById(conversationId, moplUserDetails);

		// then
		assertThat(result).isEqualTo(expectedDto);
	}

	@Test
	@DisplayName("실패: 존재하지 않는 대화방 ID로 조회하면 ConversationException이 발생한다.")
	void findConversationById_ConversationNotFound_ThrowException() {
		// given
		UUID requestUserId = UUID.randomUUID();
		MoplUserDetails moplUserDetails = mock(MoplUserDetails.class);
		given(moplUserDetails.getUserId()).willReturn(requestUserId);

		UUID conversationId = UUID.randomUUID();

		given(conversationRepository.findById(conversationId)).willReturn(Optional.empty());

		// when & then
		assertThatThrownBy(() -> conversationService.findConversationById(conversationId, moplUserDetails))
			.isInstanceOf(ConversationException.class);
	}

	@Test
	@DisplayName("실패: 대화 참여자 목록에 상대방 유저가 존재하지 않으면 UserException이 발생한다.")
	void findConversationById_WithUserNotFound_ThrowException() {
		// given
		UUID requestUserId = UUID.randomUUID();
		User requestUser = mock(User.class);
		given(requestUser.getId()).willReturn(requestUserId);

		MoplUserDetails moplUserDetails = mock(MoplUserDetails.class);
		given(moplUserDetails.getUserId()).willReturn(requestUserId);

		UUID conversationId = UUID.randomUUID();
		Conversation conversation = mock(Conversation.class);
		given(conversation.getId()).willReturn(conversationId);

		ConversationParticipant participant1 = mock(ConversationParticipant.class);

		given(conversationRepository.findById(conversationId)).willReturn(Optional.of(conversation));
		// 대화 상대 미존재
		given(conversationParticipantRepository.findByConversationId(conversationId)).willReturn(List.of(participant1));
		given(participant1.getUser()).willReturn(requestUser);

		// when & then
		assertThatThrownBy(() -> conversationService.findConversationById(conversationId, moplUserDetails))
			.isInstanceOf(UserException.class);
	}
}