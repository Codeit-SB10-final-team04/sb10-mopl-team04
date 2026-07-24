package com.team04.mopl.auth.security.interceptor;

import static org.assertj.core.api.Assertions.*;
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
import com.team04.mopl.auth.realtime.RealtimeWebSocketSessionRegistry;
import com.team04.mopl.auth.security.MoplUserDetails;
import com.team04.mopl.auth.security.jwt.JwtAuthenticationClaims;
import com.team04.mopl.auth.security.jwt.JwtTokenProvider;
import com.team04.mopl.auth.session.AuthSessionStore;
import com.team04.mopl.user.entity.UserRole;
import com.team04.mopl.watching.store.WatchingSessionStore;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class StompAuthChannelInterceptorTest {

	private final JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
	private final AuthSessionStore authSessionStore = mock(AuthSessionStore.class);
	private final WatchingSessionStore watchingSessionStore = mock(WatchingSessionStore.class);
	private final RealtimeWebSocketSessionRegistry realtimeWebSocketSessionRegistry =
		mock(RealtimeWebSocketSessionRegistry.class);
	private final MessageChannel channel = mock(MessageChannel.class);
	private final MeterRegistry meterRegistry = new SimpleMeterRegistry();

	private final StompAuthChannelInterceptor interceptor = new StompAuthChannelInterceptor(
		jwtTokenProvider,
		authSessionStore,
		watchingSessionStore,
		realtimeWebSocketSessionRegistry,
		meterRegistry
	);

	@Test
	@DisplayName("SEND 프레임은 바인딩된 인증 세션이 활성 상태이면 통과한다")
	void preSend_passesThrough_whenCommandIsSend() {
		// given
		UUID userId = UUID.randomUUID();
		UUID sessionId = UUID.randomUUID();
		StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
		accessor.setUser(authentication(userId, sessionId));
		Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
		when(authSessionStore.isActive(userId, sessionId)).thenReturn(true);

		// when
		Message<?> result = interceptor.preSend(message, channel);

		// then
		assertThat(result).isSameAs(message);
		verifyNoInteractions(jwtTokenProvider);
		verify(authSessionStore).isActive(userId, sessionId);
	}

	@Test
	@DisplayName("SUBSCRIBE 프레임은 바인딩된 인증 세션이 활성 상태이면 통과한다")
	void preSend_passesThrough_whenCommandIsSubscribe() {
		// given
		UUID userId = UUID.randomUUID();
		UUID sessionId = UUID.randomUUID();
		StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
		accessor.setUser(authentication(userId, sessionId));
		Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
		when(authSessionStore.isActive(userId, sessionId)).thenReturn(true);

		// when
		Message<?> result = interceptor.preSend(message, channel);

		// then
		assertThat(result).isSameAs(message);
		verifyNoInteractions(jwtTokenProvider);
		verify(authSessionStore).isActive(userId, sessionId);
	}

	@Test
	@DisplayName("SEND 프레임의 인증 세션이 교체되었으면 인증 예외를 던진다")
	void preSend_throwsException_whenBoundSessionIsInactive() {
		UUID userId = UUID.randomUUID();
		UUID sessionId = UUID.randomUUID();
		StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
		accessor.setUser(authentication(userId, sessionId));
		Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
		when(authSessionStore.isActive(userId, sessionId)).thenReturn(false);

		assertThatThrownBy(() -> interceptor.preSend(message, channel))
			.isInstanceOf(AuthException.class)
			.satisfies(ex -> assertThat(((AuthException)ex).getErrorCode())
				.isEqualTo(AuthErrorCode.AUTH_SESSION_INVALID));
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
			.satisfies(ex -> assertThat(((AuthException)ex).getErrorCode())
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
			.satisfies(ex -> assertThat(((AuthException)ex).getErrorCode())
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
			.satisfies(ex -> assertThat(((AuthException)ex).getErrorCode())
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
			.satisfies(ex -> assertThat(((AuthException)ex).getErrorCode())
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

		UsernamePasswordAuthenticationToken auth = (UsernamePasswordAuthenticationToken)resultAccessor.getUser();
		MoplUserDetails principal = (MoplUserDetails)auth.getPrincipal();
		assertThat(principal.getUserId()).isEqualTo(userId);
		assertThat(principal.getSessionId()).isEqualTo(sessionId);
		assertThat(principal.getEmail()).isEqualTo(email);
		assertThat(principal.getRole()).isEqualTo(UserRole.USER);
		assertThat(auth.getAuthorities()).hasSize(1);
	}

	private UsernamePasswordAuthenticationToken authentication(UUID userId, UUID sessionId) {
		MoplUserDetails principal = MoplUserDetails.authenticated(
			userId,
			sessionId,
			"test@test.com",
			UserRole.USER
		);

		return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
	}
}
