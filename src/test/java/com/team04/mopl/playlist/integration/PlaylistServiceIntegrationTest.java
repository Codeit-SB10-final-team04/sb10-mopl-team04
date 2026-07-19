package com.team04.mopl.playlist.integration;

import static org.assertj.core.api.Assertions.*;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import com.team04.mopl.auth.security.MoplUserDetails;
import com.team04.mopl.common.enums.SortDirection;
import com.team04.mopl.content.entity.Content;
import com.team04.mopl.content.entity.ContentType;
import com.team04.mopl.content.repository.ContentRepository;
import com.team04.mopl.playlist.dto.request.PlaylistCreateRequest;
import com.team04.mopl.playlist.dto.request.PlaylistPageRequest;
import com.team04.mopl.playlist.dto.request.PlaylistUpdateRequest;
import com.team04.mopl.playlist.dto.response.CursorResponsePlaylistDto;
import com.team04.mopl.playlist.dto.response.PlaylistDto;
import com.team04.mopl.playlist.entity.Playlist;
import com.team04.mopl.playlist.enums.PlaylistSortBy;
import com.team04.mopl.playlist.repository.PlaylistContentRepository;
import com.team04.mopl.playlist.repository.PlaylistRepository;
import com.team04.mopl.playlist.repository.PlaylistSubscriptionRepository;
import com.team04.mopl.playlist.service.PlaylistContentService;
import com.team04.mopl.playlist.service.PlaylistService;
import com.team04.mopl.playlist.service.PlaylistSubscriptionService;
import com.team04.mopl.support.IntegrationTestBase;
import com.team04.mopl.user.entity.User;
import com.team04.mopl.user.entity.UserRole;
import com.team04.mopl.user.repository.UserRepository;

import jakarta.persistence.EntityManager;

@Transactional
public class PlaylistServiceIntegrationTest extends IntegrationTestBase {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PlaylistRepository playlistRepository;

	@Autowired
	private PlaylistSubscriptionRepository playlistSubscriptionRepository;

	@Autowired
	private PlaylistContentRepository playlistContentRepository;

	@Autowired
	private ContentRepository contentRepository;

	@Autowired
	private PlaylistService playlistService;

	@Autowired
	private PlaylistSubscriptionService playlistSubscriptionService;

	@Autowired
	private PlaylistContentService playlistContentService;

	@Test
	@DisplayName("플레이리스트 생성 시 DTO를 반환하고 DB에 저장한다.")
	void createPlaylist_returnsDtoAndSavesToDatabase() {
		// given
		User owner = createUser("플레이리스트 소유자");

		PlaylistCreateRequest request = new PlaylistCreateRequest(
			"주말 영화",
			"주말에 볼 영화입니다."
		);

		// when
		PlaylistDto result = playlistService.createPlaylist(
			request,
			owner.getId()
		);

		// then
		assertThat(result.id()).isNotNull();
		assertThat(result.owner().userId()).isEqualTo(owner.getId());
		assertThat(result.title()).isEqualTo(request.title());
		assertThat(result.description()).isEqualTo(request.description());
		assertThat(result.subscriberCount()).isZero();
		assertThat(result.subscribedByMe()).isFalse();
		assertThat(result.contents()).isEmpty();

		entityManager.flush();
		entityManager.clear();

		Playlist saved = playlistRepository
			.findById(result.id())
			.orElseThrow();

		assertThat(saved.getOwner().getId()).isEqualTo(owner.getId());
		assertThat(saved.getTitle()).isEqualTo(request.title());
		assertThat(saved.getDescription()).isEqualTo(request.description());
	}

	@Test
	@DisplayName("플레이리스트 목록 조회 시 실제 DB 데이터를 커서 응답으로 반환한다.")
	void findAllPlaylists_returnsDatabaseData() {
		// given
		User owner = createUser("목록 소유자");

		createPlaylist(owner, "첫 번째 플레이리스트");
		createPlaylist(owner, "두 번째 플레이리스트");

		PlaylistPageRequest request = new PlaylistPageRequest(
			null,
			owner.getId(),
			null,
			null,
			null,
			10,
			SortDirection.DESCENDING,
			PlaylistSortBy.updatedAt
		);

		// when
		CursorResponsePlaylistDto result =
			playlistService.findAllPlaylists(request, owner.getId());

		// then
		assertThat(result.data()).hasSize(2);
		assertThat(result.totalCount()).isEqualTo(2L);
		assertThat(result.hasNext()).isFalse();
		assertThat(result.sortBy()).isEqualTo("updatedAt");
		assertThat(result.sortDirection())
			.isEqualTo(SortDirection.DESCENDING);
		assertThat(result.data())
			.extracting(PlaylistDto::title)
			.containsExactlyInAnyOrder(
				"첫 번째 플레이리스트",
				"두 번째 플레이리스트"
			);
	}

	@Test
	@DisplayName("플레이리스트 소유자가 수정하면 DTO와 DB에 변경 내용이 반영된다.")
	void updatePlaylist_updatesDtoAndDatabase() {
		// given
		User owner = createUser("수정 소유자");
		Playlist playlist = createPlaylist(owner, "기존 제목");

		authenticate(owner);

		PlaylistUpdateRequest request = new PlaylistUpdateRequest(
			"수정된 제목",
			"수정된 설명"
		);

		// when
		PlaylistDto result = playlistService.updatePlaylist(
			playlist.getId(),
			request,
			owner.getId()
		);

		// then
		assertThat(result.title()).isEqualTo("수정된 제목");
		assertThat(result.description()).isEqualTo("수정된 설명");

		entityManager.flush();
		entityManager.clear();

		Playlist updated = playlistRepository
			.findById(playlist.getId())
			.orElseThrow();

		assertThat(updated.getTitle()).isEqualTo("수정된 제목");
		assertThat(updated.getDescription()).isEqualTo("수정된 설명");
	}

	@Test
	@DisplayName("플레이리스트 소유자가 아닌 사용자의 수정 요청은 거부된다.")
	void updatePlaylist_throwsAccessDenied_whenNotOwner() {
		// given
		User owner = createUser("실제 소유자");
		User otherUser = createUser("다른 사용자");
		Playlist playlist = createPlaylist(owner, "변경 전 제목");

		authenticate(otherUser);

		PlaylistUpdateRequest request = new PlaylistUpdateRequest(
			"권한 없는 수정",
			null
		);

		// when, then
		assertThatThrownBy(() ->
			playlistService.updatePlaylist(
				playlist.getId(),
				request,
				otherUser.getId()
			)
		).isInstanceOf(AccessDeniedException.class);

		entityManager.clear();

		Playlist notUpdated = playlistRepository
			.findById(playlist.getId())
			.orElseThrow();

		assertThat(notUpdated.getTitle()).isEqualTo("변경 전 제목");
	}

	@Test
	@DisplayName("플레이리스트 구독과 구독 취소가 DB 관계에 반영된다.")
	void subscribeAndUnsubscribe_updatesDatabaseRelationship() {
		// given
		User owner = createUser("구독 대상 소유자");
		User subscriber = createUser("구독자");
		Playlist playlist = createPlaylist(owner, "구독할 플레이리스트");

		// when: 구독
		playlistSubscriptionService.subscribePlaylist(
			playlist.getId(),
			subscriber.getId()
		);

		// then
		assertThat(
			playlistSubscriptionRepository
				.existsByPlaylistIdAndSubscriberId(
					playlist.getId(),
					subscriber.getId()
				)
		).isTrue();

		// when: 구독 취소
		playlistSubscriptionService.unSubscribePlaylist(
			playlist.getId(),
			subscriber.getId()
		);

		// then
		assertThat(
			playlistSubscriptionRepository
				.existsByPlaylistIdAndSubscriberId(
					playlist.getId(),
					subscriber.getId()
				)
		).isFalse();
	}

	@Test
	@DisplayName("플레이리스트 콘텐츠 추가와 삭제가 DB 관계에 반영된다.")
	void addAndDeleteContent_updatesDatabaseRelationship() {
		// given
		User owner = createUser("콘텐츠 추가 소유자");
		Playlist playlist = createPlaylist(owner, "영화 모음");
		Content content = createContent("인터스텔라");

		authenticate(owner);

		// when: 콘텐츠 추가
		playlistContentService.addContentToPlaylist(
			playlist.getId(),
			content.getId(),
			owner.getId()
		);

		// then
		assertThat(
			playlistContentRepository.existsByPlaylistIdAndContentId(
				playlist.getId(),
				content.getId()
			)
		).isTrue();

		// when: 콘텐츠 삭제
		playlistContentService.deleteContentFromPlaylist(
			playlist.getId(),
			content.getId(),
			owner.getId()
		);

		// then
		assertThat(
			playlistContentRepository.existsByPlaylistIdAndContentId(
				playlist.getId(),
				content.getId()
			)
		).isFalse();
	}

	@Test
	@DisplayName("플레이리스트 삭제 시 행을 제거하지 않고 deletedAt을 기록한다.")
	void softDeletePlaylist_marksDeletedAt() {
		// given
		User owner = createUser("삭제 소유자");
		Playlist playlist = createPlaylist(owner, "삭제 대상");

		authenticate(owner);

		// when
		playlistService.softDeletePlaylist(
			playlist.getId(),
			owner.getId()
		);

		// then
		entityManager.flush();
		entityManager.clear();

		Playlist deleted = playlistRepository
			.findById(playlist.getId())
			.orElseThrow();

		assertThat(deleted.getDeletedAt()).isNotNull();
		assertThat(
			playlistRepository.findByIdWithOwnerAndDeletedAtIsNull(
				playlist.getId()
			)
		).isEmpty();
	}

	private User createUser(String name) {
		UUID userId = UUID.randomUUID();

		jdbcTemplate.update("""
				INSERT INTO users (
					id,
					name,
					email,
					email_type,
					role,
					is_locked,
					created_at,
					updated_at
				)
				VALUES (?, ?, ?, 'REAL', 'USER', false, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
				""",
			userId,
			name,
			userId + "@test.com"
		);

		return userRepository.findById(userId).orElseThrow();
	}

	private Playlist createPlaylist(User owner, String title) {
		return playlistRepository.saveAndFlush(
			Playlist.builder()
				.owner(owner)
				.title(title)
				.description("통합 테스트 플레이리스트 설명")
				.build()
		);
	}

	private Content createContent(String title) {
		return contentRepository.saveAndFlush(
			Content.builder()
				.title(title)
				.type(ContentType.movie)
				.description("통합 테스트 콘텐츠 설명")
				.thumbnailUrl("https://test.local/thumbnail.png")
				.build()
		);
	}

	private void authenticate(User user) {
		MoplUserDetails principal = MoplUserDetails.authenticated(
			user.getId(),
			user.getEmail(),
			UserRole.USER
		);

		UsernamePasswordAuthenticationToken authentication =
			new UsernamePasswordAuthenticationToken(
				principal,
				null,
				principal.getAuthorities()
			);

		SecurityContextHolder.getContext()
			.setAuthentication(authentication);
	}
}
