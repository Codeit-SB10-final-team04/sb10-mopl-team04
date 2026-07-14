package com.team04.mopl.conversation.repository.qdsl;

import static org.assertj.core.api.Assertions.*;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import com.team04.mopl.config.JpaAuditingConfig;
import com.team04.mopl.config.QuerydslConfig;
import com.team04.mopl.conversation.entity.Conversation;
import com.team04.mopl.conversation.repository.ConversationRepository;

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
	@DisplayName("성공: 전달받은 ID 리스트에 해당하는 대화방 엔티티 목록을 반환한다.")
	void findAllByIdIn_Success() {
		// given
		Conversation conv1 = em.persist(Conversation.create());
		Conversation conv2 = em.persist(Conversation.create());
		Conversation conv3 = em.persist(Conversation.create());

		em.flush();
		em.clear();

		List<UUID> targetIds = List.of(conv1.getId(), conv2.getId());

		// when
		List<Conversation> result = conversationRepository.findAllByIdIn(targetIds);

		// then
		assertThat(result).hasSize(2);
		assertThat(result).extracting(Conversation::getId)
			.containsExactlyInAnyOrder(conv1.getId(), conv2.getId())
			.doesNotContain(conv3.getId());
	}

	@Test
	@DisplayName("성공: 빈 리스트를 전달하면 쿼리를 실행하지 않고(또는 in 조건 회피) 빈 리스트를 반환한다.")
	void findAllByIdIn_EmptyList_Success() {
		// given
		List<UUID> emptyIds = Collections.emptyList();

		// when
		List<Conversation> result = conversationRepository.findAllByIdIn(emptyIds);

		// then
		assertThat(result).isEmpty();
	}

	@Test
	@DisplayName("성공: 요청한 ID 목록 중 RDB에 존재하지 않는 ID가 섞여 있어도 존재하는 데이터만 정확히 반환한다.")
	void findAllByIdIn_PartialMatch_Success() {
		// given
		Conversation conv1 = em.persist(Conversation.create());
		Conversation conv2 = em.persist(Conversation.create());

		em.flush();
		em.clear();

		UUID notExistingId = UUID.randomUUID();
		List<UUID> targetIds = List.of(conv1.getId(), conv2.getId(), notExistingId);

		// when
		List<Conversation> result = conversationRepository.findAllByIdIn(targetIds);

		// then
		assertThat(result).hasSize(2);
		assertThat(result).extracting(Conversation::getId)
			.containsExactlyInAnyOrder(conv1.getId(), conv2.getId());
	}
}