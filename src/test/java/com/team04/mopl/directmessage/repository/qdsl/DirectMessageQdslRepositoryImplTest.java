package com.team04.mopl.directmessage.repository.qdsl;

import static org.assertj.core.api.AssertionsForInterfaceTypes.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;

import com.team04.mopl.common.enums.SortDirection;
import com.team04.mopl.config.JpaAuditingConfig;
import com.team04.mopl.config.QuerydslConfig;
import com.team04.mopl.conversation.entity.Conversation;
import com.team04.mopl.directmessage.dto.request.DirectMessagePagedRequest;
import com.team04.mopl.directmessage.entity.DirectMessage;
import com.team04.mopl.directmessage.exception.DirectMessageErrorCode;
import com.team04.mopl.directmessage.exception.DirectMessageException;
import com.team04.mopl.directmessage.repository.DirectMessageRepository;
import com.team04.mopl.user.entity.User;

@DataJpaTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:conversation-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
	"spring.jpa.hibernate.ddl-auto=none",
	"spring.sql.init.mode=always",
	"spring.sql.init.schema-locations=classpath:schema-h2-direct-message_querydsl-test.sql",
	"spring.jpa.database-platform=com.team04.mopl.config.H2TestDialect"
})
@Import({QuerydslConfig.class, JpaAuditingConfig.class})
public class DirectMessageQdslRepositoryImplTest {
	@Autowired
	private DirectMessageRepository directMessageRepository;

	@Autowired
	private TestEntityManager em;

	@Test
	@DisplayName("성공: 첫 페이지 조회 시 지정된 limit + 1 개수만큼 반환한다.")
	void findDirectMessages_FirstPage_Success() {
		// given
		User sender = createUser("sender");
		User receiver = createUser("receiver");
		Conversation conversation = em.persist(Conversation.create());
		int limit = 3;

		for (int i = 0; i < 5; i++) {
			em.persist(DirectMessage.builder()
				.conversation(conversation)
				.sender(sender)
				.receiver(receiver)
				.content("메시지 " + i)
				.build());
		}
		em.flush();
		em.clear();

		// 첫 페이지 요청 (커서 null)
		DirectMessagePagedRequest request = new DirectMessagePagedRequest(
			null,
			null,
			limit,
			SortDirection.DESCENDING,
			"createdAt"
		);

		// when
		List<DirectMessage> result = directMessageRepository.findDirectMessagesByCursor(conversation.getId(), request);

		// then
		assertThat(result).hasSize(limit + 1);
	}

	@Test
	@DisplayName("성공: 내림차순(DESC) 페이지네이션 시 커서보다 과거의 DM을 조회한다.")
	void findDirectMessages_CursorDesc_Success() {
		// given
		User sender = createUser("sender");
		User receiver = createUser("receiver");
		Conversation conversation = em.persist(Conversation.create());
		Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

		DirectMessage msg1 = createMessage(conversation, sender, receiver, now.minus(20, ChronoUnit.SECONDS)); // 더 과거
		DirectMessage msg2 = createMessage(conversation, sender, receiver, now.minus(10, ChronoUnit.SECONDS)); // 커서 기준

		em.flush();
		em.clear();

		DirectMessagePagedRequest request = new DirectMessagePagedRequest(
			msg2.getCreatedAt().toString(),
			msg2.getId(),
			10,
			SortDirection.DESCENDING,
			"createdAt"
		);

		// when
		List<DirectMessage> result = directMessageRepository.findDirectMessagesByCursor(conversation.getId(), request);

		// then
		assertThat(result).hasSize(1);
		assertThat(result.get(0).getId()).isEqualTo(msg1.getId());
	}

	@Test
	@DisplayName("성공: 오름차순(ASC) 페이지네이션 시 커서보다 미래의 DM을 조회한다.")
	void findDirectMessages_CursorAsc_Success() {
		// given
		User sender = createUser("sender");
		User receiver = createUser("receiver");
		Conversation conversation = em.persist(Conversation.create());
		Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

		DirectMessage msg1 = createMessage(conversation, sender, receiver, now.minus(20, ChronoUnit.SECONDS)); // 커서 기준
		DirectMessage msg2 = createMessage(conversation, sender, receiver, now.minus(10, ChronoUnit.SECONDS)); // 더 미래

		em.flush();
		em.clear();

		DirectMessagePagedRequest request = new DirectMessagePagedRequest(
			msg1.getCreatedAt().toString(),
			msg1.getId(),
			10,
			SortDirection.ASCENDING,
			"createdAt"
		);

		// when
		List<DirectMessage> result = directMessageRepository.findDirectMessagesByCursor(conversation.getId(), request);

		// then
		assertThat(result).hasSize(1);
		assertThat(result.get(0).getId()).isEqualTo(msg2.getId());
	}

	@Test
	@DisplayName("성공: 데이터가 없는 대화방의 메시지 개수를 조회하면 0L을 반환한다.")
	void countDirectMessage_Empty_Success() {
		// given
		UUID conversationId = UUID.randomUUID();
		DirectMessagePagedRequest request = new DirectMessagePagedRequest(
			null,
			null,
			10,
			SortDirection.DESCENDING,
			"createdAt"
		);

		// when
		Long count = directMessageRepository.countDirectMessage(conversationId, request);

		// then
		assertThat(count).isEqualTo(0L);
	}

	@Test
	@DisplayName("실패: 지원하지 않는 정렬 기준(sortBy) 전달 시 예외가 발생한다.")
	void findDirectMessages_InvalidSortBy_Fail() {
		// given
		UUID conversationId = UUID.randomUUID();
		DirectMessagePagedRequest request = new DirectMessagePagedRequest(
			null,
			null,
			10,
			SortDirection.DESCENDING,
			"invalidSortField"
		);

		// when & then
		assertThatThrownBy(() -> directMessageRepository.findDirectMessagesByCursor(conversationId, request))
			.isInstanceOf(DirectMessageException.class)
			.hasMessageContaining(DirectMessageErrorCode.DM_INVALID_FORMAT.getMessage());
	}

	@Test
	@DisplayName("실패: 잘못된 문자열 형식의 커서(cursor) 전달 시 파싱 예외가 발생한다.")
	void findDirectMessages_InvalidCursorFormat_Fail() {
		// given
		DirectMessagePagedRequest request = new DirectMessagePagedRequest(
			"invalid-date-format", UUID.randomUUID(), 10, SortDirection.DESCENDING, "createdAt"
		);

		// when & then
		assertThatThrownBy(() -> directMessageRepository.findDirectMessagesByCursor(UUID.randomUUID(), request))
			.isInstanceOf(DirectMessageException.class)
			.hasMessageContaining(DirectMessageErrorCode.DM_INVALID_FORMAT.getMessage());
	}

	// 임시 사용자 생성 메서드
	private User createUser(String name) {
		UUID userId = UUID.randomUUID();

		em.getEntityManager().createNativeQuery(
				"INSERT INTO users (id, name, email, email_type, role, is_locked, created_at, updated_at) "
					+ "VALUES (:id, :name, :email, :emailType, :role, false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)"
			)
			.setParameter("id", userId)
			.setParameter("name", name)
			.setParameter("email", name + "@test.com")
			.setParameter("emailType", "REAL")
			.setParameter("role", "USER")
			.executeUpdate();

		return em.getEntityManager().getReference(User.class, userId);
	}

	private DirectMessage createMessage(Conversation conversation, User sender, User receiver, Instant time) {
		DirectMessage msg = em.persist(DirectMessage.builder()
			.conversation(conversation)
			.sender(sender)
			.receiver(receiver)
			.content("내용")
			.build());

		em.getEntityManager().createNativeQuery("UPDATE direct_messages SET created_at = :time WHERE id = :id")
			.setParameter("time", time)
			.setParameter("id", msg.getId())
			.executeUpdate();

		ReflectionTestUtils.setField(msg, "createdAt", time);
		return msg;
	}
}
