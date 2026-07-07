package com.team04.mopl.auth.security.provider;

import java.time.Instant;
import java.util.Optional;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.team04.mopl.auth.entity.TemporaryPassword;
import com.team04.mopl.auth.repository.TemporaryPasswordRepository;
import com.team04.mopl.auth.security.MoplUserDetails;
import com.team04.mopl.user.entity.User;
import com.team04.mopl.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * 이메일/비밀번호 기반 로그인을 처리하는 Spring Security 인증 Provider
 *
 * - 실제 비밀번호와 사용자가 입력한 비밀번호를 비교하고, 실패하면 만료되지 않은 임시 비밀번호와 비교
 * - 임시 비밀번호가 만료된 경우 DB에서 삭제
 */
@Component
@RequiredArgsConstructor
public class MoplAuthenticationProvider implements AuthenticationProvider {

	private final UserRepository userRepository;
	private final TemporaryPasswordRepository temporaryPasswordRepository;
	private final PasswordEncoder passwordEncoder;

	// 로그인 요청의 이메일과 비밀번호를 검증
	@Override
	@Transactional(noRollbackFor = AuthenticationException.class)
	public Authentication authenticate(Authentication authentication) throws AuthenticationException {
		String email = authentication.getName();
		String password = String.valueOf(authentication.getCredentials());

		// 로그인 파라미터의 username 값을 이메일로 사용해 사용자 조회
		User user = userRepository.findByEmail(email)
			.orElseThrow(() -> new BadCredentialsException("이메일 또는 비밀번호가 올바르지 않습니다."));

		if (user.isLocked()) {
			throw new LockedException("잠긴 계정입니다.");
		}

		if (!matchesPassword(user, password)) {
			throw new BadCredentialsException("이메일 또는 비밀번호가 올바르지 않습니다.");
		}

		// 인증 성공 시 SecurityContext에 저장할 Principal 생성
		MoplUserDetails principal = MoplUserDetails.from(user);

		// 인증 후 credentials은 보관할 필요가 겂으므로 null
		return new UsernamePasswordAuthenticationToken(
			principal,
			null,
			principal.getAuthorities()
		);
	}

	// 해당 Provider가 처리할 인증 타입인지 확인
	@Override
	public boolean supports(Class<?> authentication) {
		return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
	}

	// 실제 비밀번호 또는 유효한 임시 비밀번호와 일치하는지 확인
	private boolean matchesPassword(User user, String rawPassword) {
		if (matchesUserPassword(user, rawPassword)) {
			return true;
		}

		return matchesTemporaryPassword(user, rawPassword);
	}

	// 사용자 실제 비밀번호 일치 여부 확인
	private boolean matchesUserPassword(User user, String rawPassword) {
		return user.isPasswordLoginSupported()
			&& passwordEncoder.matches(rawPassword, user.getPasswordHashForAuthentication());
	}

	// 임시 비밀번호 일치 여부 확인
	private boolean matchesTemporaryPassword(User user, String rawPassword) {
		Optional<TemporaryPassword> temporaryPassword = temporaryPasswordRepository.findByUser_Id(user.getId());

		// 임시 비밀번호가 발급된 적 없으면 실패
		if (temporaryPassword.isEmpty()) {
			return false;
		}

		TemporaryPassword password = temporaryPassword.get();

		// 만료된 임시 비밀번호는 삭제하고 인증 실패로 처리
		if (password.isExpired(Instant.now())) {
			temporaryPasswordRepository.deleteByUser_Id(user.getId());

			return false;
		}

		// 만료 전 임시 비밀번호 해시값과 입력 비밀번호 비교
		return passwordEncoder.matches(rawPassword, password.getPasswordHash());
	}
}
