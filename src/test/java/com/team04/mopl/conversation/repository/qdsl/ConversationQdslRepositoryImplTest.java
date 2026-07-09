package com.team04.mopl.conversation.repository.qdsl;

import static org.assertj.core.api.Assertions.*;

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
import com.team04.mopl.conversation.dto.request.ConversationPageRequest;
import com.team04.mopl.conversation.entity.Conversation;
import com.team04.mopl.conversation.entity.ConversationParticipant;
import com.team04.mopl.conversation.exception.ConversationErrorCode;
import com.team04.mopl.conversation.exception.ConversationException;
import com.team04.mopl.conversation.repository.ConversationRepository;
import com.team04.mopl.directmessage.entity.DirectMessage;
import com.team04.mopl.user.entity.User;

@DataJpaTest(properties = {
	"spring.datasource.url=jdbc:h2:mem:conversation-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
	"spring.jpa.hibernate.ddl-auto=none",
	"spring.sql.init.mode=always",
	"spring.sql.init.schema-locations=classpath:schema-h2-conversation_querydsl-test.sql"
})
@Import({QuerydslConfig.class, JpaAuditingConfig.class})
class ConversationQdslRepositoryImplTest {

	@Autowired
	private ConversationRepository conversationRepository;

	@Autowired
	private TestEntityManager em;

	@Test
	@DisplayName("성공: 조건이 없는 첫 페이지 조회 시 지정된 limit + 1 개수만큼 반환한다.")
	void searchConversation_FirstPage_Success() {
		// given
		User requestUser = createUser("requestUser");
		int limit = 5;

		// limit + 1 (6개) 이상의 대화방 생성
		for (int i = 0; i < 7; i++) {
			Conversation conversation = em.persist(Conversation.create());
			em.persist(ConversationParticipant.builder()
				.conversation(conversation)
				.user(requestUser)
				.build());
		}

		em.flush();
		em.clear();

		ConversationPageRequest request = new ConversationPageRequest(
			null,
			null,
			null,
			limit,
			SortDirection.DESCENDING,
			"createdAt"
		);

		// when
		List<Conversation> result = conversationRepository.searchConversation(request, requestUser.getId());

		// then
		assertThat(result).hasSize(limit + 1);
	}

	@Test
	@DisplayName("성공: 내림차순(DESC) 커서 페이지네이션 적용 시 커서 시간 및 ID보다 작은 데이터를 조회한다.")
	void searchConversation_CursorPaginationDesc_Success() {
		// given
		User requestUser = createUser("requestUser");
		Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

		Conversation conv1 = em.persist(Conversation.create());
		em.persist(ConversationParticipant.builder().conversation(conv1).user(requestUser).build());

		Conversation conv2 = em.persist(Conversation.create());
		em.persist(ConversationParticipant.builder().conversation(conv2).user(requestUser).build());

		Conversation conv3 = em.persist(Conversation.create());
		em.persist(ConversationParticipant.builder().conversation(conv3).user(requestUser).build());

		em.flush();

		updateConversationTime(conv1, now.minus(30, ChronoUnit.SECONDS));
		updateConversationTime(conv2, now.minus(20, ChronoUnit.SECONDS));
		updateConversationTime(conv3, now.minus(10, ChronoUnit.SECONDS));

		em.clear();

		// 내림차순 조회: conv2(-20초) 기준, 더 과거인 conv1(-30초)만 나와야 함
		ConversationPageRequest request = new ConversationPageRequest(
			null,
			conv2.getCreatedAt().toString(),
			conv2.getId(),
			10,
			SortDirection.DESCENDING,
			"createdAt"
		);

		// when
		List<Conversation> result = conversationRepository.searchConversation(request, requestUser.getId());

		// then
		assertThat(result).hasSize(1);
		assertThat(result.get(0).getId()).isEqualTo(conv1.getId());
	}

	@Test
	@DisplayName("성공: 오름차순(ASC) 커서 페이지네이션 적용 시 커서 시간 및 ID보다 큰 데이터를 조회한다.")
	void searchConversation_CursorPaginationAsc_Success() {
		// given
		User requestUser = createUser("requestUser");
		Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);

		Conversation conv1 = em.persist(Conversation.create());
		em.persist(ConversationParticipant.builder().conversation(conv1).user(requestUser).build());

		Conversation conv2 = em.persist(Conversation.create());
		em.persist(ConversationParticipant.builder().conversation(conv2).user(requestUser).build());

		Conversation conv3 = em.persist(Conversation.create());
		em.persist(ConversationParticipant.builder().conversation(conv3).user(requestUser).build());

		em.flush();

		updateConversationTime(conv1, now.minus(30, ChronoUnit.SECONDS));
		updateConversationTime(conv2, now.minus(20, ChronoUnit.SECONDS));
		updateConversationTime(conv3, now.minus(10, ChronoUnit.SECONDS));

		em.clear();

		// 오름차순 조회: conv2(-20초) 기준, 더 미래인 conv3(-10초)만 나와야 함
		ConversationPageRequest request = new ConversationPageRequest(
			null,
			conv2.getCreatedAt().toString(),
			conv2.getId(),
			10,
			SortDirection.ASCENDING,
			"createdAt"
		);

		// when
		List<Conversation> result = conversationRepository.searchConversation(request, requestUser.getId());

		// then
		assertThat(result).hasSize(1);
		assertThat(result.get(0).getId()).isEqualTo(conv3.getId());
	}

	@Test
	@DisplayName("성공: 커서 값만 전달되고 idAfter가 null이면 첫 페이지 조회처럼 동작한다.")
	void searchConversation_OnlyCursorProvided_Success() {
		// given
		User requestUser = createUser("requestUser");
		Conversation conv1 = em.persist(Conversation.create());
		em.persist(ConversationParticipant.builder().conversation(conv1).user(requestUser).build());

		em.flush();
		em.clear();

		// cursor는 있지만 idAfter가 null인 경우
		ConversationPageRequest request = new ConversationPageRequest(
			null,
			conv1.getCreatedAt().toString(),
			null,
			10,
			SortDirection.DESCENDING,
			"createdAt"
		);

		// when
		List<Conversation> result = conversationRepository.searchConversation(request, requestUser.getId());

		// then
		assertThat(result).hasSize(1);
		assertThat(result.get(0).getId()).isEqualTo(conv1.getId());
	}

	@Test
	@DisplayName("성공: idAfter만 전달되고 커서 값이 null이면 첫 페이지 조회처럼 동작한다.")
	void searchConversation_OnlyIdAfterProvided_Success() {
		// given
		User requestUser = createUser("requestUser");
		Conversation conv1 = em.persist(Conversation.create());
		em.persist(ConversationParticipant.builder().conversation(conv1).user(requestUser).build());

		em.flush();
		em.clear();

		// idAfter는 있지만 cursor가 null인 경우
		ConversationPageRequest request = new ConversationPageRequest(
			null,
			null,
			conv1.getId(),
			10,
			SortDirection.DESCENDING,
			"createdAt"
		);

		// when
		List<Conversation> result = conversationRepository.searchConversation(request, requestUser.getId());

		// then
		assertThat(result).hasSize(1);
		assertThat(result.get(0).getId()).isEqualTo(conv1.getId());
	}

	@Test
	@DisplayName("성공: 대화 상대의 닉네임에 검색어가 포함되어 있으면 해당 대화를 반환한다.")
	void searchConversation_SearchByOpponentName_Success() {
		// given
		User requestUser = createUser("requestUser");
		User opponentUser = createUser("opponentNickname"); // 검색 대상

		Conversation conversation = em.persist(Conversation.create());

		// 요청자 & 상대방 모두 대화방에 참여
		em.persist(ConversationParticipant.builder().conversation(conversation).user(requestUser).build());
		em.persist(ConversationParticipant.builder().conversation(conversation).user(opponentUser).build());

		em.flush();
		em.clear();

		ConversationPageRequest request = new ConversationPageRequest(
			"opponentNickname",
			null,
			null,
			10,
			SortDirection.DESCENDING,
			"createdAt"
		);

		// when
		List<Conversation> result = conversationRepository.searchConversation(request, requestUser.getId());
		Long count = conversationRepository.countConversation(request, requestUser.getId());

		// then
		assertThat(result).isNotEmpty();
		assertThat(result.get(0).getId()).isEqualTo(conversation.getId());
		assertThat(count).isGreaterThan(0L);
	}

	@Test
	@DisplayName("성공: 대화 메시지 내용에 검색어가 포함되어 있으면 해당 대화를 반환한다.")
	void searchConversation_SearchByMessageContent_Success() {
		// given
		User requestUser = createUser("requestUser");
		User otherUser = createUser("otherUser");

		Conversation conversation = em.persist(Conversation.create());
		em.persist(ConversationParticipant.builder().conversation(conversation).user(requestUser).build());

		DirectMessage message = DirectMessage.builder()
			.sender(otherUser)
			.receiver(requestUser)
			.conversation(conversation)
			.content("이것은 특정 메시지 내용 입니다.")
			.build();
		em.persist(message);

		em.flush();
		em.clear();

		ConversationPageRequest request = new ConversationPageRequest(
			"특정 메시지 내용",
			null,
			null,
			10,
			SortDirection.DESCENDING,
			"createdAt"
		);

		// when
		List<Conversation> result = conversationRepository.searchConversation(request, requestUser.getId());

		// then
		assertThat(result).isNotEmpty();
		assertThat(result.get(0).getId()).isEqualTo(conversation.getId());
	}

	@Test
	@DisplayName("성공: 데이터가 없을 때 countConversation을 호출하면 0L을 안전하게 반환한다 (NPE 방어 확인)")
	void countConversation_Empty_Success() {
		// given
		UUID requestUserId = UUID.randomUUID();
		ConversationPageRequest request = new ConversationPageRequest(
			"없는키워드",
			null,
			null,
			10,
			SortDirection.DESCENDING,
			"createdAt"
		);

		// when
		Long count = conversationRepository.countConversation(request, requestUserId);

		// then
		assertThat(count).isEqualTo(0L);
	}

	@Test
	@DisplayName("실패: 지원하지 않는 정렬 기준(sortBy) 입력 시 예외가 발생한다.")
	void searchConversation_InvalidSortBy_Fail() {
		// given
		UUID requestUserId = UUID.randomUUID();
		ConversationPageRequest request = new ConversationPageRequest(
			null,
			null,
			null,
			10,
			SortDirection.DESCENDING,
			"invalidField"
		);

		// when & then
		assertThatThrownBy(() -> conversationRepository.searchConversation(request, requestUserId))
			.isInstanceOf(ConversationException.class)
			.hasMessageContaining(ConversationErrorCode.CONVERSATION_INVALID_FORMAT.getMessage());
	}

	@Test
	@DisplayName("실패: 잘못된 형태의 커서 값(문자열) 입력 시 파싱 예외가 발생한다.")
	void searchConversation_InvalidCursorFormat_Fail() {
		// given
		UUID requestUserId = UUID.randomUUID();
		ConversationPageRequest request = new ConversationPageRequest(
			null,
			"invalid-cursor",
			UUID.randomUUID(),
			10,
			SortDirection.DESCENDING,
			"createdAt"
		);

		// when & then
		assertThatThrownBy(() -> conversationRepository.searchConversation(request, requestUserId))
			.isInstanceOf(ConversationException.class)
			.hasMessageContaining(ConversationErrorCode.CONVERSATION_INVALID_FORMAT.getMessage());
	}

	// 임시 사용자 생성 메서드
	private User createUser(String name) {
		UUID userId = UUID.randomUUID();

		// Native Query로 INSERT
		em.getEntityManager().createNativeQuery(
				"INSERT INTO users (id, name, email, email_type, role, is_locked, created_at, updated_at) "
					+ "VALUES (:id, :name, :email, 'REAL', 'USER', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)"
			)
			.setParameter("id", userId)
			.setParameter("name", name)
			.setParameter("email", name + "@test.com")
			.executeUpdate();

		return em.getEntityManager().getReference(User.class, userId);
	}

	private void updateConversationTime(Conversation conversation, Instant time) {
		em.getEntityManager().createNativeQuery(
				"UPDATE conversations SET created_at = :time WHERE id = :id"
			)
			.setParameter("time", time)
			.setParameter("id", conversation.getId())
			.executeUpdate();

		// 메모리의 객체도 변경된 시간을 가지도록 세팅 (request 생성 시 getCreatedAt()을 호출하기 때문)
		ReflectionTestUtils.setField(conversation, "createdAt", time);
	}
}