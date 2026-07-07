package com.team04.mopl.auth.session;

import static org.assertj.core.api.AssertionsForClassTypes.*;
import static org.mockito.BDDMockito.*;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.assertj.core.api.ThrowableAssert;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.team04.mopl.auth.entity.AuthSession;
import com.team04.mopl.auth.repository.AuthSessionRepository;
import com.team04.mopl.user.entity.User;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

@ExtendWith(MockitoExtension.class)
class DbAuthSessionStoreTest {
	@Mock
	private AuthSessionRepository authSessionRepository;

	@Mock
	private EntityManager entityManager;

	@Mock
	private User user;

	@Mock
	private AuthSession existingSession;

	@InjectMocks
	private DbAuthSessionStore authSessionStore;

	@Test
	@DisplayName("사용자가 존재하면 비관적 락을 걸고 기존 세션 삭제 후 새 인증 세션을 저장한다")
	void replace_saveNewSession_whenUserExists() {
		// given
		UUID userId = UUID.randomUUID();
		UUID sessionId = UUID.randomUUID();
		String refreshTokenHash = "refresh-token-hash";
		Instant issuedAt = Instant.parse("2026-06-24T00:00:00Z");
		Instant accessExpiresAt = issuedAt.plusSeconds(1800);
		Instant refreshExpiresAt = issuedAt.plusSeconds(1209600);

		given(entityManager.find(User.class, userId, LockModeType.PESSIMISTIC_WRITE))
			.willReturn(user);

		given(authSessionRepository.save(any(AuthSession.class)))
			.willAnswer(invocation -> invocation.getArgument(0));

		// when
		AuthSession result = authSessionStore.replace(
			userId,
			sessionId,
			refreshTokenHash,
			accessExpiresAt,
			refreshExpiresAt,
			issuedAt
		);

		// then
		assertThat(result.getSessionId()).isEqualTo(sessionId);
		assertThat(result.getUser()).isEqualTo(user);
		assertThat(result.getRefreshTokenHash()).isEqualTo(refreshTokenHash);
		assertThat(result.getAccessExpiresAt()).isEqualTo(accessExpiresAt);
		assertThat(result.getRefreshExpiresAt()).isEqualTo(refreshExpiresAt);
		assertThat(result.getUpdatedAt()).isEqualTo(issuedAt);

		ArgumentCaptor<AuthSession> captor = ArgumentCaptor.forClass(AuthSession.class);

		InOrder inOrder = inOrder(entityManager, authSessionRepository);
		inOrder.verify(entityManager).find(User.class, userId, LockModeType.PESSIMISTIC_WRITE);
		inOrder.verify(authSessionRepository).deleteByUser_Id(userId);
		inOrder.verify(authSessionRepository).flush();
		inOrder.verify(authSessionRepository).save(captor.capture());

		AuthSession savedSession = captor.getValue();

		assertThat(savedSession.getSessionId()).isEqualTo(sessionId);
		assertThat(savedSession.getUser()).isEqualTo(user);
		assertThat(savedSession.getRefreshTokenHash()).isEqualTo(refreshTokenHash);
	}

	@Test
	@DisplayName("userId가 null이면 세션 교체에 실패한다")
	void replace_throwNullPointerException_whenUserIdIsNull() {
		// given
		UUID sessionId = UUID.randomUUID();
		Instant issuedAt = Instant.parse("2026-06-24T00:00:00Z");

		// when
		ThrowableAssert.ThrowingCallable action = () -> authSessionStore.replace(
			null,
			sessionId,
			"refresh-token-hash",
			issuedAt.plusSeconds(1800),
			issuedAt.plusSeconds(1209600),
			issuedAt
		);

		// then
		assertThatThrownBy(action)
			.isInstanceOf(NullPointerException.class)
			.hasMessage("userId는 필수입니다.");

		verifyNoInteractions(entityManager);
		verifyNoInteractions(authSessionRepository);
	}

	@Test
	@DisplayName("userId에 해당하는 사용자가 없으면 세션 교체에 실패한다")
	void replace_throwIllegalArgumentException_whenUserDoesNotExist() {
		// given
		UUID userId = UUID.randomUUID();
		UUID sessionId = UUID.randomUUID();
		Instant issuedAt = Instant.parse("2026-06-24T00:00:00Z");

		given(entityManager.find(User.class, userId, LockModeType.PESSIMISTIC_WRITE))
			.willReturn(null);

		// when
		ThrowableAssert.ThrowingCallable action = () -> authSessionStore.replace(
			userId,
			sessionId,
			"refresh-token-hash",
			issuedAt.plusSeconds(1800),
			issuedAt.plusSeconds(1209600),
			issuedAt
		);

		// then
		assertThatThrownBy(action)
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("사용자를 찾을 수 없습니다.");

		verify(entityManager).find(User.class, userId, LockModeType.PESSIMISTIC_WRITE);
		verifyNoInteractions(authSessionRepository);
	}

	@Test
	@DisplayName("userId 기준으로 활성 인증 세션을 조회한다")
	void findByUserId_returnSession_whenSessionExists() {
		// given
		UUID userId = UUID.randomUUID();

		given(authSessionRepository.findByUser_Id(userId))
			.willReturn(Optional.of(existingSession));

		// when
		Optional<AuthSession> result = authSessionStore.findByUserId(userId);

		// then
		assertThat(result).contains(existingSession);

		verify(authSessionRepository).findByUser_Id(userId);
		verifyNoInteractions(entityManager);
	}

	@Test
	@DisplayName("userId 기준 조회 시 세션이 없으면 empty를 반환한다")
	void findByUserId_returnEmpty_whenSessionDoesNotExist() {
		// given
		UUID userId = UUID.randomUUID();

		given(authSessionRepository.findByUser_Id(userId))
			.willReturn(Optional.empty());

		// when
		Optional<AuthSession> result = authSessionStore.findByUserId(userId);

		// then
		assertThat(result).isEmpty();

		verify(authSessionRepository).findByUser_Id(userId);
		verifyNoInteractions(entityManager);
	}

	@Test
	@DisplayName("userId가 null이면 활성 인증 세션 조회에 실패한다")
	void findByUserId_throwNullPointerException_whenUserIdIsNull() {
		// given

		// when
		ThrowableAssert.ThrowingCallable action = () -> authSessionStore.findByUserId(null);

		// then
		assertThatThrownBy(action)
			.isInstanceOf(NullPointerException.class)
			.hasMessage("userId는 필수입니다.");

		verifyNoInteractions(entityManager);
		verifyNoInteractions(authSessionRepository);
	}

	@Test
	@DisplayName("userId와 sessionId가 일치하면 활성 세션으로 판단한다")
	void isActive_returnTrue_whenUserIdAndSessionIdMatch() {
		// given
		UUID userId = UUID.randomUUID();
		UUID sessionId = UUID.randomUUID();

		given(authSessionRepository.existsByUser_IdAndSessionId(userId, sessionId))
			.willReturn(true);

		// when
		boolean result = authSessionStore.isActive(userId, sessionId);

		// then
		assertThat(result).isTrue();

		verify(authSessionRepository).existsByUser_IdAndSessionId(userId, sessionId);
		verifyNoInteractions(entityManager);
	}

	@Test
	@DisplayName("sessionId가 다르면 활성 세션이 아니라고 판단한다")
	void isActive_returnFalse_whenSessionIdDoesNotMatch() {
		// given
		UUID userId = UUID.randomUUID();
		UUID sessionId = UUID.randomUUID();

		given(authSessionRepository.existsByUser_IdAndSessionId(userId, sessionId))
			.willReturn(false);

		// when
		boolean result = authSessionStore.isActive(userId, sessionId);

		// then
		assertThat(result).isFalse();

		verify(authSessionRepository).existsByUser_IdAndSessionId(userId, sessionId);
		verifyNoInteractions(entityManager);
	}

	@Test
	@DisplayName("userId가 null이면 활성 세션이 아니라고 판단한다")
	void isActive_returnFalse_whenUserIdIsNull() {
		// given
		UUID sessionId = UUID.randomUUID();

		// when
		boolean result = authSessionStore.isActive(null, sessionId);

		// then
		assertThat(result).isFalse();

		verifyNoInteractions(entityManager);
		verifyNoInteractions(authSessionRepository);
	}

	@Test
	@DisplayName("sessionId가 null이면 활성 세션이 아니라고 판단한다")
	void isActive_returnFalse_whenSessionIdIsNull() {
		// given
		UUID userId = UUID.randomUUID();

		// when
		boolean result = authSessionStore.isActive(userId, null);

		// then
		assertThat(result).isFalse();

		verifyNoInteractions(entityManager);
		verifyNoInteractions(authSessionRepository);
	}

	@Test
	@DisplayName("refresh token hash 기준으로 인증 세션을 조회한다")
	void findByRefreshTokenHash_returnSession_whenSessionExists() {
		// given
		String refreshTokenHash = "refresh-token-hash";

		given(authSessionRepository.findByRefreshTokenHash(refreshTokenHash))
			.willReturn(Optional.of(existingSession));

		// when
		Optional<AuthSession> result = authSessionStore.findByRefreshTokenHash(refreshTokenHash);

		// then
		assertThat(result).contains(existingSession);

		verify(authSessionRepository).findByRefreshTokenHash(refreshTokenHash);
		verifyNoInteractions(entityManager);
	}

	@Test
	@DisplayName("refresh token hash 기준 조회 시 세션이 없으면 empty를 반환한다")
	void findByRefreshTokenHash_returnEmpty_whenSessionDoesNotExist() {
		// given
		String refreshTokenHash = "refresh-token-hash";

		given(authSessionRepository.findByRefreshTokenHash(refreshTokenHash))
			.willReturn(Optional.empty());

		// when
		Optional<AuthSession> result = authSessionStore.findByRefreshTokenHash(refreshTokenHash);

		// then
		assertThat(result).isEmpty();

		verify(authSessionRepository).findByRefreshTokenHash(refreshTokenHash);
		verifyNoInteractions(entityManager);
	}

	@Test
	@DisplayName("refresh token hash가 null이면 인증 세션 조회 시 empty를 반환한다")
	void findByRefreshTokenHash_returnEmpty_whenRefreshTokenHashIsNull() {
		// given

		// when
		Optional<AuthSession> result = authSessionStore.findByRefreshTokenHash(null);

		// then
		assertThat(result).isEmpty();

		verifyNoInteractions(entityManager);
		verifyNoInteractions(authSessionRepository);
	}

	@Test
	@DisplayName("refresh token hash가 공백이면 인증 세션 조회 시 empty를 반환한다")
	void findByRefreshTokenHash_returnEmpty_whenRefreshTokenHashIsBlank() {
		// given

		// when
		Optional<AuthSession> result = authSessionStore.findByRefreshTokenHash("   ");

		// then
		assertThat(result).isEmpty();

		verifyNoInteractions(entityManager);
		verifyNoInteractions(authSessionRepository);
	}

	@Test
	@DisplayName("현재 refresh token hash와 userId가 일치하면 인증 세션을 갱신한다")
	void refresh_updateSessionAndReturnSession_whenCurrentRefreshTokenHashAndUserIdMatch() {
		// given
		UUID userId = UUID.randomUUID();
		String currentRefreshTokenHash = "current-refresh-token-hash";
		String newRefreshTokenHash = "new-refresh-token-hash";
		Instant refreshedAt = Instant.parse("2026-06-24T01:00:00Z");
		Instant accessExpiresAt = refreshedAt.plusSeconds(1800);
		Instant refreshExpiresAt = refreshedAt.plusSeconds(1209600);

		given(authSessionRepository.findByRefreshTokenHash(currentRefreshTokenHash))
			.willReturn(Optional.of(existingSession));
		given(existingSession.getUser()).willReturn(user);
		given(user.getId()).willReturn(userId);

		// when
		Optional<AuthSession> result = authSessionStore.refresh(
			userId,
			currentRefreshTokenHash,
			newRefreshTokenHash,
			accessExpiresAt,
			refreshExpiresAt,
			refreshedAt
		);

		// then
		assertThat(result).contains(existingSession);

		verify(authSessionRepository).findByRefreshTokenHash(currentRefreshTokenHash);
		verify(existingSession).refresh(
			newRefreshTokenHash,
			accessExpiresAt,
			refreshExpiresAt,
			refreshedAt
		);
		verifyNoInteractions(entityManager);
	}

	@Test
	@DisplayName("현재 refresh token hash에 해당하는 인증 세션이 없으면 empty를 반환한다")
	void refresh_returnEmpty_whenCurrentRefreshTokenHashDoesNotExist() {
		// given
		UUID userId = UUID.randomUUID();
		String currentRefreshTokenHash = "current-refresh-token-hash";
		String newRefreshTokenHash = "new-refresh-token-hash";
		Instant refreshedAt = Instant.parse("2026-06-24T01:00:00Z");
		Instant accessExpiresAt = refreshedAt.plusSeconds(1800);
		Instant refreshExpiresAt = refreshedAt.plusSeconds(1209600);

		given(authSessionRepository.findByRefreshTokenHash(currentRefreshTokenHash))
			.willReturn(Optional.empty());

		// when
		Optional<AuthSession> result = authSessionStore.refresh(
			userId,
			currentRefreshTokenHash,
			newRefreshTokenHash,
			accessExpiresAt,
			refreshExpiresAt,
			refreshedAt
		);

		// then
		assertThat(result).isEmpty();

		verify(authSessionRepository).findByRefreshTokenHash(currentRefreshTokenHash);
		verify(existingSession, never()).refresh(
			any(String.class),
			any(Instant.class),
			any(Instant.class),
			any(Instant.class)
		);
		verifyNoInteractions(entityManager);
	}

	@Test
	@DisplayName("인증 세션의 userId가 요청 userId와 다르면 empty를 반환한다")
	void refresh_returnEmpty_whenUserIdDoesNotMatch() {
		// given
		UUID requestUserId = UUID.randomUUID();
		UUID sessionUserId = UUID.randomUUID();

		String currentRefreshTokenHash = "current-refresh-token-hash";
		String newRefreshTokenHash = "new-refresh-token-hash";
		Instant refreshedAt = Instant.parse("2026-06-24T01:00:00Z");
		Instant accessExpiresAt = refreshedAt.plusSeconds(1800);
		Instant refreshExpiresAt = refreshedAt.plusSeconds(1209600);

		given(authSessionRepository.findByRefreshTokenHash(currentRefreshTokenHash))
			.willReturn(Optional.of(existingSession));
		given(existingSession.getUser()).willReturn(user);
		given(user.getId()).willReturn(sessionUserId);

		// when
		Optional<AuthSession> result = authSessionStore.refresh(
			requestUserId,
			currentRefreshTokenHash,
			newRefreshTokenHash,
			accessExpiresAt,
			refreshExpiresAt,
			refreshedAt
		);

		// then
		assertThat(result).isEmpty();

		verify(authSessionRepository).findByRefreshTokenHash(currentRefreshTokenHash);
		verify(existingSession, never()).refresh(
			any(String.class),
			any(Instant.class),
			any(Instant.class),
			any(Instant.class)
		);
		verifyNoInteractions(entityManager);
	}

	@Test
	@DisplayName("userId가 null이면 refresh에 실패한다")
	void refresh_throwNullPointerException_whenUserIdIsNull() {
		// given
		Instant refreshedAt = Instant.parse("2026-06-24T01:00:00Z");

		// when
		ThrowableAssert.ThrowingCallable action = () -> authSessionStore.refresh(
			null,
			"current-refresh-token-hash",
			"new-refresh-token-hash",
			refreshedAt.plusSeconds(1800),
			refreshedAt.plusSeconds(1209600),
			refreshedAt
		);

		// then
		assertThatThrownBy(action)
			.isInstanceOf(NullPointerException.class)
			.hasMessage("userId는 필수입니다.");

		verifyNoInteractions(entityManager);
		verifyNoInteractions(authSessionRepository);
	}

	@Test
	@DisplayName("현재 refresh token hash가 공백이면 refresh에 실패한다")
	void refresh_throwIllegalArgumentException_whenCurrentRefreshTokenHashIsBlank() {
		// given
		UUID userId = UUID.randomUUID();
		Instant refreshedAt = Instant.parse("2026-06-24T01:00:00Z");

		// when
		ThrowableAssert.ThrowingCallable action = () -> authSessionStore.refresh(
			userId,
			"   ",
			"new-refresh-token-hash",
			refreshedAt.plusSeconds(1800),
			refreshedAt.plusSeconds(1209600),
			refreshedAt
		);

		// then
		assertThatThrownBy(action)
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("현재 Refresh Token 해시는 필수입니다.");

		verifyNoInteractions(entityManager);
		verifyNoInteractions(authSessionRepository);
	}

	@Test
	@DisplayName("현재 refresh token hash가 null이면 refresh에 실패한다")
	void refresh_throwIllegalArgumentException_whenCurrentRefreshTokenHashIsNull() {
		// given
		UUID userId = UUID.randomUUID();
		Instant refreshedAt = Instant.parse("2026-06-24T01:00:00Z");

		// when
		ThrowableAssert.ThrowingCallable action = () -> authSessionStore.refresh(
			userId,
			null,
			"new-refresh-token-hash",
			refreshedAt.plusSeconds(1800),
			refreshedAt.plusSeconds(1209600),
			refreshedAt
		);

		// then
		assertThatThrownBy(action)
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("현재 Refresh Token 해시는 필수입니다.");

		verifyNoInteractions(entityManager);
		verifyNoInteractions(authSessionRepository);
	}

	@Test
	@DisplayName("새 refresh token hash가 공백이면 refresh에 실패한다")
	void refresh_throwIllegalArgumentException_whenNewRefreshTokenHashIsBlank() {
		// given
		UUID userId = UUID.randomUUID();
		Instant refreshedAt = Instant.parse("2026-06-24T01:00:00Z");

		// when
		ThrowableAssert.ThrowingCallable action = () -> authSessionStore.refresh(
			userId,
			"current-refresh-token-hash",
			"   ",
			refreshedAt.plusSeconds(1800),
			refreshedAt.plusSeconds(1209600),
			refreshedAt
		);

		// then
		assertThatThrownBy(action)
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("새 Refresh Token 해시는 필수입니다.");

		verifyNoInteractions(entityManager);
		verifyNoInteractions(authSessionRepository);
	}

	@Test
	@DisplayName("새 refresh token hash가 null이면 refresh에 실패한다")
	void refresh_throwIllegalArgumentException_whenNewRefreshTokenHashIsNull() {
		// given
		UUID userId = UUID.randomUUID();
		Instant refreshedAt = Instant.parse("2026-06-24T01:00:00Z");

		// when
		ThrowableAssert.ThrowingCallable action = () -> authSessionStore.refresh(
			userId,
			"current-refresh-token-hash",
			null,
			refreshedAt.plusSeconds(1800),
			refreshedAt.plusSeconds(1209600),
			refreshedAt
		);

		// then
		assertThatThrownBy(action)
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("새 Refresh Token 해시는 필수입니다.");

		verifyNoInteractions(entityManager);
		verifyNoInteractions(authSessionRepository);
	}

	@Test
	@DisplayName("사용자 인증 세션 삭제를 repository에 위임한다")
	void deleteByUserId_deleteSession_whenUserIdIsProvided() {
		// given
		UUID userId = UUID.randomUUID();

		// when
		authSessionStore.deleteByUserId(userId);

		// then
		verify(authSessionRepository).deleteByUser_Id(userId);
		verifyNoInteractions(entityManager);
	}

	@Test
	@DisplayName("userId가 null이면 세션 삭제에 실패한다")
	void deleteByUserId_throwNullPointerException_whenUserIdIsNull() {
		// given

		// when
		ThrowableAssert.ThrowingCallable action = () -> authSessionStore.deleteByUserId(null);

		// then
		assertThatThrownBy(action)
			.isInstanceOf(NullPointerException.class)
			.hasMessage("userId는 필수입니다.");

		verifyNoInteractions(entityManager);
		verifyNoInteractions(authSessionRepository);
	}

	@Test
	@DisplayName("userId와 sessionId 기준으로 특정 인증 세션 삭제를 repository에 위임한다")
	void delete_deleteSession_whenUserIdAndSessionIdAreProvided() {
		// given
		UUID userId = UUID.randomUUID();
		UUID sessionId = UUID.randomUUID();

		// when
		authSessionStore.delete(userId, sessionId);

		// then
		verify(authSessionRepository).deleteByUser_IdAndSessionId(userId, sessionId);
		verifyNoInteractions(entityManager);
	}
	@Test
	@DisplayName("userId가 null이면 특정 인증 세션 삭제에 실패한다")
	void delete_throwNullPointerException_whenUserIdIsNull() {
		// given
		UUID sessionId = UUID.randomUUID();

		// when
		ThrowableAssert.ThrowingCallable action = () -> authSessionStore.delete(null, sessionId);

		// then
		assertThatThrownBy(action)
			.isInstanceOf(NullPointerException.class)
			.hasMessage("userId는 필수입니다.");

		verifyNoInteractions(entityManager);
		verifyNoInteractions(authSessionRepository);
	}

	@Test
	@DisplayName("sessionId가 null이면 특정 인증 세션 삭제에 실패한다")
	void delete_throwNullPointerException_whenSessionIdIsNull() {
		// given
		UUID userId = UUID.randomUUID();

		// when
		ThrowableAssert.ThrowingCallable action = () -> authSessionStore.delete(userId, null);

		// then
		assertThatThrownBy(action)
			.isInstanceOf(NullPointerException.class)
			.hasMessage("sessionId는 필수입니다.");

		verifyNoInteractions(entityManager);
		verifyNoInteractions(authSessionRepository);
	}
}