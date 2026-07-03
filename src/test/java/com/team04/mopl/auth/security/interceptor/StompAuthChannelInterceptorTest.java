package com.team04.mopl.auth.security.interceptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import com.team04.mopl.auth.exception.AuthErrorCode;
import com.team04.mopl.auth.exception.AuthException;
import com.team04.mopl.auth.security.MoplUserDetails;
import com.team04.mopl.auth.security.jwt.JwtAuthenticationClaims;
import com.team04.mopl.auth.security.jwt.JwtTokenProvider;
import com.team04.mopl.auth.session.AuthSessionStore;
import com.team04.mopl.user.entity.UserRole;

class StompAuthChannelInterceptorTest {

	private final JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
	private final AuthSessionStore authSessionStore = mock(AuthSessionStore.class);
	private final MessageChannel channel = mock(MessageChannel.class);

	private final StompAuthChannelInterceptor interceptor = new StompAuthChannelInterceptor(
		jwtTokenProvider,
		authSessionStore
	);

	@Test
	@DisplayName("SEND 프레임은 인증 검사 없이 그대로 통과한다")
	void preSend_passesThrough_whenCommandIsSend() {
		// given
		StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
		Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

		// when
		Message<?> result = interceptor.preSend(message, channel);

		// then
		assertThat(result).isSameAs(message);
		verifyNoInteractions(jwtTokenProvider, authSessionStore);
	}

	@Test
	@DisplayName("SUBSCRIBE 프레임은 인증 검사 없이 그대로 통과한다")
	void preSend_passesThrough_whenCommandIsSubscribe() {
		// given
		StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
		Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

		// when
		Message<?> result = interceptor.preSend(message, channel);

		// then
		assertThat(result).isSameAs(message);
		verifyNoInteractions(jwtTokenProvider, authSessionStore);
	}

	@Test
	@DisplayName("CONNECT 시 Authorization 헤더가 없으면 AuthException을 던진다")
	void preSend_throwsException_whenAuthorizationHeaderIsMissing() {
		// given
		StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
		accessor.setLeaveMutable(true);
		Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

		// when & then
		assertThatThrownBy(() -> interceptor.preSend(message, channel))
			.isInstanceOf(AuthException.class)
			.satisfies(ex -> assertThat(((AuthException) ex).getErrorCode())
				.isEqualTo(AuthErrorCode.AUTH_INVALID_ACCESS_TOKEN));
	}

	@Test
	@DisplayName("CONNECT 시 Bearer 접두어 없이 토큰을 보내면 AuthException을 던진다")
	void preSend_throwsException_whenBearerPrefixIsMissing() {
		// given
		StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
		accessor.setLeaveMutable(true);
		accessor.addNativeHeader("Authorization", "InvalidToken");
		Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

		// when & then
		assertThatThrownBy(() -> interceptor.preSend(message, channel))
			.isInstanceOf(AuthException.class)
			.satisfies(ex -> assertThat(((AuthException) ex).getErrorCode())
				.isEqualTo(AuthErrorCode.AUTH_INVALID_ACCESS_TOKEN));
	}

	@Test
	@DisplayName("CONNECT 시 Bearer 뒤에 공백 토큰이면 AuthException을 던진다")
	void preSend_throwsException_whenTokenIsBlank() {
		// given
		StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
		accessor.setLeaveMutable(true);
		accessor.addNativeHeader("Authorization", "Bearer    ");
		Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

		// when & then
		assertThatThrownBy(() -> interceptor.preSend(message, channel))
			.isInstanceOf(AuthException.class)
			.satisfies(ex -> assertThat(((AuthException) ex).getErrorCode())
				.isEqualTo(AuthErrorCode.AUTH_INVALID_ACCESS_TOKEN));
	}

	@Test
	@DisplayName("CONNECT 시 세션이 비활성이면 AuthException을 던진다")
	void preSend_throwsException_whenSessionIsInactive() {
		// given
		UUID userId = UUID.randomUUID();
		UUID sessionId = UUID.randomUUID();
		String token = "valid-token";

		StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
		accessor.setLeaveMutable(true);
		accessor.addNativeHeader("Authorization", "Bearer " + token);
		Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

		JwtAuthenticationClaims claims = new JwtAuthenticationClaims(userId, sessionId, "test@test.com", UserRole.USER);
		when(jwtTokenProvider.parseAccessToken(token)).thenReturn(claims);
		when(authSessionStore.isActive(userId, sessionId)).thenReturn(false);

		// when & then
		assertThatThrownBy(() -> interceptor.preSend(message, channel))
			.isInstanceOf(AuthException.class)
			.satisfies(ex -> assertThat(((AuthException) ex).getErrorCode())
				.isEqualTo(AuthErrorCode.AUTH_SESSION_INVALID));
	}

	@Test
	@DisplayName("CONNECT 시 정상 토큰이면 Principal이 바인딩된다")
	void preSend_bindsPrincipal_whenTokenIsValid() {
		// given
		UUID userId = UUID.randomUUID();
		UUID sessionId = UUID.randomUUID();
		String email = "test@test.com";
		String token = "valid-token";

		StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
		accessor.setLeaveMutable(true);
		accessor.addNativeHeader("Authorization", "Bearer " + token);
		Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

		JwtAuthenticationClaims claims = new JwtAuthenticationClaims(userId, sessionId, email, UserRole.USER);
		when(jwtTokenProvider.parseAccessToken(token)).thenReturn(claims);
		when(authSessionStore.isActive(userId, sessionId)).thenReturn(true);

		// when
		Message<?> result = interceptor.preSend(message, channel);

		// then
		StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
		assertThat(resultAccessor.getUser()).isNotNull();
		assertThat(resultAccessor.getUser()).isInstanceOf(UsernamePasswordAuthenticationToken.class);

		UsernamePasswordAuthenticationToken auth = (UsernamePasswordAuthenticationToken) resultAccessor.getUser();
		MoplUserDetails principal = (MoplUserDetails) auth.getPrincipal();
		assertThat(principal.getUserId()).isEqualTo(userId);
		assertThat(principal.getEmail()).isEqualTo(email);
		assertThat(principal.getRole()).isEqualTo(UserRole.USER);
		assertThat(auth.getAuthorities()).hasSize(1);
	}
}
