package com.team04.mopl.user.repository.qdsl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.team04.mopl.common.enums.SortDirection;
import com.team04.mopl.config.QuerydslConfig;
import com.team04.mopl.user.dto.request.UserPageRequest;
import com.team04.mopl.user.dto.response.UserCursorPage;
import com.team04.mopl.user.dto.response.UserDto;
import com.team04.mopl.user.entity.UserRole;
import com.team04.mopl.user.enums.UserSortBy;
import com.team04.mopl.user.exception.UserErrorCode;
import com.team04.mopl.user.exception.UserException;
import com.team04.mopl.user.repository.UserRepository;

@DataJpaTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:user-querydsl-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
	"spring.jpa.hibernate.ddl-auto=none",
	"spring.sql.init.mode=always",
	"spring.sql.init.schema-locations=classpath:schema-h2-user_querydsl-test.sql"
})
@Import(QuerydslConfig.class)
class UserQdslRepositoryImplTest {

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private final UUID alphaUser = UUID.fromString("10000000-0000-0000-0000-000000000001");
	private final UUID bravoUser = UUID.fromString("10000000-0000-0000-0000-000000000002");
	private final UUID charlieUser = UUID.fromString("10000000-0000-0000-0000-000000000003");
	private final UUID deltaUser = UUID.fromString("10000000-0000-0000-0000-000000000004");

	private final Instant alphaCreatedAt = Instant.parse("2026-07-01T00:00:00Z");
	private final Instant bravoCreatedAt = Instant.parse("2026-07-02T00:00:00Z");
	private final Instant charlieCreatedAt = Instant.parse("2026-07-03T00:00:00Z");
	private final Instant deltaCreatedAt = Instant.parse("2026-07-04T00:00:00Z");

	@BeforeEach
	void setUp() {
		insertFixtures();
	}

	@Test
	@DisplayName("이메일, 권한, 잠금 상태 조건으로 사용자 목록을 이름 오름차순 조회한다")
	void findUsers_returnCursorPage_whenFilterAndSortByNameAsc() {
		// given
		UserPageRequest request = new UserPageRequest(
			"test",
			UserRole.USER,
			false,
			null,
			null,
			1,
			SortDirection.ASCENDING,
			UserSortBy.name
		);

		// when
		UserCursorPage result = userRepository.findUsers(request);

		// then
		assertThat(result.users())
			.extracting(UserDto::id)
			.containsExactly(alphaUser);
		assertThat(result.hasNext()).isTrue();
		assertThat(result.totalCount()).isEqualTo(2L);
	}

	@Test
	@DisplayName("이름 커서가 있으면 커서 이후 사용자 목록을 조회한다")
	void findUsers_returnCursorPage_whenAfterNameCursor() {
		// given
		UserPageRequest request = new UserPageRequest(
			"test",
			UserRole.USER,
			false,
			"Alpha",
			alphaUser,
			5,
			SortDirection.ASCENDING,
			UserSortBy.name
		);

		// when
		UserCursorPage result = userRepository.findUsers(request);

		// then
		assertThat(result.users())
			.extracting(UserDto::id)
			.containsExactly(bravoUser);
		assertThat(result.hasNext()).isFalse();
		assertThat(result.totalCount()).isEqualTo(2L);
	}

	@Test
	@DisplayName("생성일 내림차순으로 사용자 목록을 조회한다")
	void findUsers_returnCursorPage_whenSortByCreatedAtDesc() {
		// given
		UserPageRequest request = new UserPageRequest(
			null,
			null,
			null,
			null,
			null,
			3,
			SortDirection.DESCENDING,
			UserSortBy.createdAt
		);

		// when
		UserCursorPage result = userRepository.findUsers(request);

		// then
		assertThat(result.users())
			.extracting(UserDto::id)
			.containsExactly(deltaUser, charlieUser, bravoUser);
		assertThat(result.hasNext()).isTrue();
		assertThat(result.totalCount()).isEqualTo(4L);
	}

	@Test
	@DisplayName("이메일 오름차순으로 사용자 목록을 조회한다")
	void findUsers_returnCursorPage_whenSortByEmailAsc() {
		// given
		UserPageRequest request = new UserPageRequest(
			null,
			null,
			null,
			null,
			null,
			4,
			SortDirection.ASCENDING,
			UserSortBy.email
		);

		// when
		UserCursorPage result = userRepository.findUsers(request);

		// then
		assertThat(result.users())
			.extracting(UserDto::id)
			.containsExactly(alphaUser, bravoUser, charlieUser, deltaUser);
		assertThat(result.hasNext()).isFalse();
		assertThat(result.totalCount()).isEqualTo(4L);
	}

	@Test
	@DisplayName("잠금 상태 오름차순 커서가 있으면 false 이후 true 사용자 목록을 조회한다")
	void findUsers_returnCursorPage_whenAfterLockedFalseCursorAsc() {
		// given
		UserPageRequest request = new UserPageRequest(
			null,
			null,
			null,
			"false",
			charlieUser,
			5,
			SortDirection.ASCENDING,
			UserSortBy.isLocked
		);

		// when
		UserCursorPage result = userRepository.findUsers(request);

		// then
		assertThat(result.users())
			.extracting(UserDto::id)
			.containsExactly(deltaUser);
		assertThat(result.hasNext()).isFalse();
		assertThat(result.totalCount()).isEqualTo(4L);
	}

	@Test
	@DisplayName("잠금 상태 내림차순 커서가 있으면 true 이후 false 사용자 목록을 조회한다")
	void findUsers_returnCursorPage_whenAfterLockedTrueCursorDesc() {
		// given
		UserPageRequest request = new UserPageRequest(
			null,
			null,
			null,
			"true",
			deltaUser,
			5,
			SortDirection.DESCENDING,
			UserSortBy.isLocked
		);

		// when
		UserCursorPage result = userRepository.findUsers(request);

		// then
		assertThat(result.users())
			.extracting(UserDto::id)
			.containsExactly(charlieUser, bravoUser, alphaUser);
		assertThat(result.hasNext()).isFalse();
		assertThat(result.totalCount()).isEqualTo(4L);
	}

	@Test
	@DisplayName("권한 오름차순 커서가 있으면 ADMIN 이후 USER 사용자 목록을 조회한다")
	void findUsers_returnCursorPage_whenAfterRoleAdminCursorAsc() {
		// given
		UserPageRequest request = new UserPageRequest(
			null,
			null,
			null,
			"ADMIN",
			charlieUser,
			5,
			SortDirection.ASCENDING,
			UserSortBy.role
		);

		// when
		UserCursorPage result = userRepository.findUsers(request);

		// then
		assertThat(result.users())
			.extracting(UserDto::id)
			.containsExactly(alphaUser, bravoUser, deltaUser);
		assertThat(result.hasNext()).isFalse();
		assertThat(result.totalCount()).isEqualTo(4L);
	}

	@Test
	@DisplayName("권한 내림차순 커서가 있으면 USER 이후 ADMIN 사용자 목록을 조회한다")
	void findUsers_returnCursorPage_whenAfterRoleUserCursorDesc() {
		// given
		UserPageRequest request = new UserPageRequest(
			null,
			null,
			null,
			"USER",
			alphaUser,
			5,
			SortDirection.DESCENDING,
			UserSortBy.role
		);

		// when
		UserCursorPage result = userRepository.findUsers(request);

		// then
		assertThat(result.users())
			.extracting(UserDto::id)
			.containsExactly(charlieUser);
		assertThat(result.hasNext()).isFalse();
		assertThat(result.totalCount()).isEqualTo(4L);
	}

	@Test
	@DisplayName("생성일 커서 형식이 올바르지 않으면 UserException을 던진다")
	void findUsers_throwUserException_whenCreatedAtCursorIsInvalid() {
		// given
		UserPageRequest request = new UserPageRequest(
			null,
			null,
			null,
			"invalid-cursor",
			alphaUser,
			5,
			SortDirection.DESCENDING,
			UserSortBy.createdAt
		);

		// when, then
		assertThatThrownBy(() -> userRepository.findUsers(request))
			.isInstanceOfSatisfying(UserException.class, exception ->
				assertThat(exception.getErrorCode()).isEqualTo(UserErrorCode.USER_INVALID_CURSOR)
			);
	}

	private void insertFixtures() {
		insertUser(
			alphaUser,
			"Alpha",
			"alpha@test.com",
			UserRole.USER,
			false,
			alphaCreatedAt
		);
		insertUser(
			bravoUser,
			"Bravo",
			"bravo@test.com",
			UserRole.USER,
			false,
			bravoCreatedAt
		);
		insertUser(
			charlieUser,
			"Charlie",
			"charlie@test.com",
			UserRole.ADMIN,
			false,
			charlieCreatedAt
		);
		insertUser(
			deltaUser,
			"Delta",
			"delta@sample.com",
			UserRole.USER,
			true,
			deltaCreatedAt
		);
	}

	private void insertUser(
		UUID userId,
		String name,
		String email,
		UserRole role,
		boolean locked,
		Instant createdAt
	) {
		jdbcTemplate.update("""
				INSERT INTO users (
					id, name, email, email_type, password_hash, profile_image_url,
					role, is_locked, created_at, updated_at
				)
				VALUES (?, ?, ?, 'REAL', NULL, ?, ?, ?, ?, ?)
				""",
			userId,
			name,
			email,
			"https://example.com/" + name,
			role.toString(),
			locked,
			Timestamp.from(createdAt),
			Timestamp.from(createdAt)
		);
	}
}
