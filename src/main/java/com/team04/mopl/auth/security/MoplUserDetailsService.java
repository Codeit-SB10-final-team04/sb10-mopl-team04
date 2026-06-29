package com.team04.mopl.auth.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.team04.mopl.user.entity.User;
import com.team04.mopl.user.repository.UserRepository;

import lombok.RequiredArgsConstructor;

/**
 * 로그인 과정에서 사용자 정보를 조회하는 서비스
 *
 * - formLogin의 username 파라미터로 전달된 이메일을 활용하여 사용자 조회
 * - 조회한 User 엔티티를 MoplUserDetails로 변환해 Spring Security에 전달
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MoplUserDetailsService implements UserDetailsService {

	private final UserRepository userRepository;

	// username으로 전달된 이메일을 기준으로 로그인 사용자 조회
	@Override
	public UserDetails loadUserByUsername(String username) {
		User user = userRepository.findByEmail(username)
			.orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다."));

		if (!user.isPasswordLoginSupported()) {
			throw new UsernameNotFoundException("사용자를 찾을 수 없습니다.");
		}

		return MoplUserDetails.from(user);
	}
}
