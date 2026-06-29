package com.team04.mopl.playlist.repository.qdsl;

import static com.team04.mopl.playlist.entity.QPlaylist.*;
import static com.team04.mopl.playlist.entity.QPlaylistSubscription.*;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.team04.mopl.common.enums.SortDirection;
import com.team04.mopl.playlist.dto.request.PlaylistSearchRequest;
import com.team04.mopl.playlist.dto.response.PlaylistCursorPage;
import com.team04.mopl.playlist.dto.row.PlaylistRow;
import com.team04.mopl.playlist.entity.QPlaylistSubscription;
import com.team04.mopl.playlist.enums.PlaylistSortBy;
import com.team04.mopl.playlist.exception.PlaylistErrorCode;
import com.team04.mopl.playlist.exception.PlaylistException;
import com.team04.mopl.playlist.repository.PlaylistQdslRepository;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

@Repository
public class PlaylistQdslRepositoryImpl implements PlaylistQdslRepository {

	private final JPAQueryFactory jpaQueryFactory;

	// SpotBugs EI_EXPOSE_REP2:
	// JPAQueryFactory는 요청 데이터나 컬렉션처럼 방어 복사할 객체가 아니라
	// Spring 컨테이너가 생명 주기를 관리하는 QueryDSL 실행용 Bean
	// Repository는 이 Bean을 의존성으로 주입 받아 사용하는 것이 의도된 사용 방식 -> 경고 억제
	@SuppressFBWarnings(
		value = "EI_EXPOSE_REP2",
		justification = "JPAQueryFactory는 Spring에서 관리하는 Bean으로 주입받아 사용하기에 방어 복사 필요 없음"
	)
	public PlaylistQdslRepositoryImpl(JPAQueryFactory jpaQueryFactory) {
		this.jpaQueryFactory = jpaQueryFactory;
	}

	@Override
	public PlaylistCursorPage findAllPlaylists(PlaylistSearchRequest request) {
		String cursor = request.cursor();
		PlaylistSortBy sortBy = request.sortBy();
		SortDirection sortDirection = request.sortDirection();
		UUID idAfter = request.idAfter();
		int limit = request.limit();

		// 구독자 수를 세는 SQL 집계식을 Java 객체로 표현 = `COUNT(playlist_subscription.id)`
		// playlist_subscriptions table을 구독자 수 집계를 위
		NumberExpression<Long> subscriberCount = playlistSubscription.id.count();

		List<PlaylistRow> rows = jpaQueryFactory
			.select(Projections.constructor(
				PlaylistRow.class,
				playlist, // playlist 객체
				subscriberCount, // 구독자 수
				playlist.owner.id,
				playlist.owner.name,
				playlist.owner.profileImageUrl
			))
			.from(playlist)
			.leftJoin(playlist.owner)
			.leftJoin(playlistSubscription).on(
				// playlist_subscriptions.playlist_id = playlists.id
				playlistSubscription.playlist.id.eq(playlist.id)
			)
			.where(
				// 논리 삭제되지 않은 플레이리스트 (playlists.deleted_at IS NULL)
				playlist.deletedAt.isNull(),
				// normalizedKeyword를 포함한 title or description (playlists.title LIKE %normalizedKeyword%)
				keywordLike(request.normalizedKeyword()),
				// 입력된 ownerId가 생성한 플레이리스트인지
				ownerIdEq(request.ownerIdEqual()),
				// 입력된 subscriberId가 구독한 플레이리스트인지
				subscriberIdEq(request.subscriberIdEqual()),
				// updatedAt 커서 조건
				updateCursorCondition(
					cursor,
					sortBy,
					sortDirection,
					idAfter
				)
			)
			// 구독자 수 수집을 위해 playlists.id 등으로 groupBy
			.groupBy(
				playlist.id,
				playlist.owner.id,
				playlist.owner.name,
				playlist.owner.profileImageUrl
			)
			// subscriberCount 커서 조건 (집계 조건)
			.having(subscriberCountCursorCondition(
				cursor,
				sortBy,
				sortDirection,
				idAfter,
				subscriberCount
			))
			// 커서 조건에 따라 정렬
			.orderBy(orderCondition(
					sortDirection,
					sortBy,
					subscriberCount
				)
			)
			// 최대 수집 횟수 (다음 데이터 존재 여부를 위해 `+1`)
			.limit(limit + 1)
			// 데이터 가져와!
			.fetch();

		// 다음 페이지 여부
		boolean hasNext = rows.size() > limit;
		List<PlaylistRow> playlistRows = hasNext
			? rows.subList(0, limit)
			: rows;

		// 조건에 따른 전체 데이터 개수 조회
		Long totalCount = getTotalCount(request);

		return new PlaylistCursorPage(playlistRows, hasNext, totalCount);
	}

	private BooleanExpression keywordLike(String keyword) {
		if (keyword == null) {
			return null;
		}

		return playlist.title.containsIgnoreCase(keyword)
			.or(playlist.description.containsIgnoreCase(keyword));
	}

	private BooleanExpression ownerIdEq(UUID ownerId) {
		if (ownerId == null) {
			return null;
		}

		return playlist.owner.id.eq(ownerId);
	}

	private BooleanExpression subscriberIdEq(UUID subscriberId) {
		if (subscriberId == null) {
			return null;
		}

		// subscriberFilter라는 별칭을 가진 QPlaylistSubscription 생성
		QPlaylistSubscription ps = new QPlaylistSubscription("subscriberFilter");

		return JPAExpressions
			.selectOne()
			.from(ps)
			.where(
				// 현재 행의 플레이리스트 id와 동일한지
				ps.playlist.id.eq(playlist.id),
				// 현재 행의 플레이리스트 구독자 id가 요청에 입력된 구독자 id와 동일한지
				ps.subscriber.id.eq(subscriberId)
			)
			// 동일하다면 true
			.exists();
	}

	private BooleanExpression updateCursorCondition(
		String cursor,
		PlaylistSortBy sortBy,
		SortDirection sortDirection,
		UUID idAfter
	) {
		if (cursor == null || sortBy.equals(PlaylistSortBy.subscribeCount)) {
			return null;
		}

		// sortBy 종류에 따라 cursor parser
		if (sortBy.equals(PlaylistSortBy.updatedAt)) {
			Instant cursorUpdatedAt = parserStringToInstant(cursor);
			return updateCursor(
				sortDirection,
				idAfter,
				cursorUpdatedAt
			);
		}

		throw new PlaylistException(PlaylistErrorCode.INVALID_INPUT);
	}

	// String 타입의 cursor -> Instant 타입으로 parse
	private Instant parserStringToInstant(String cursor) {
		try {
			return Instant.parse(cursor);
		} catch (DateTimeParseException e) {
			throw new PlaylistException(PlaylistErrorCode.INVALID_INPUT, e)
				.addDetail("cursor", cursor);
		}
	}

	private BooleanExpression updateCursor(
		SortDirection sortDirection,
		UUID idAfter,
		Instant cursorUpdatedAt
	) {
		if (sortDirection.equals(SortDirection.DESCENDING)) {
			return playlist.updatedAt.lt(cursorUpdatedAt)
				.or(
					playlist.updatedAt.eq(cursorUpdatedAt)
						.and(playlist.id.lt(idAfter))
				);
		}

		if (sortDirection.equals(SortDirection.ASCENDING)) {
			return playlist.updatedAt.gt(cursorUpdatedAt)
				.or(
					playlist.updatedAt.eq(cursorUpdatedAt)
						.and(playlist.id.gt(idAfter))
				);
		}

		throw new PlaylistException(PlaylistErrorCode.INVALID_INPUT);
	}

	// sortBy가 subscriberCount일 때
	private BooleanExpression subscriberCountCursorCondition(
		String cursor,
		PlaylistSortBy sortBy,
		SortDirection sortDirection,
		UUID idAfter,
		NumberExpression<Long> subscriberCount
	) {
		if (cursor == null || sortBy.equals(PlaylistSortBy.updatedAt)) {
			return null;
		}

		// cursor를 Long으로 parser 후 subscriberCount cursor 조건 생성
		if (sortBy.equals(PlaylistSortBy.subscribeCount)) {
			Long cursorSubscriberCount = parserStringToLong(cursor);
			return subscriptionCountCursor(
				sortDirection,
				idAfter,
				cursorSubscriberCount,
				subscriberCount
			);
		}

		throw new PlaylistException(PlaylistErrorCode.INVALID_INPUT);
	}

	// String 타입 cursor -> Long 타입으로 parse
	private Long parserStringToLong(String cursor) {
		try {
			return Long.parseLong(cursor);
		} catch (NumberFormatException e) {
			throw new PlaylistException(PlaylistErrorCode.INVALID_INPUT, e)
				.addDetail("cursor", cursor);
		}
	}

	// 정렬 방향에 따라 subscriberCount cursor 조건 생성
	private BooleanExpression subscriptionCountCursor(
		SortDirection sortDirection,
		UUID idAfter,
		Long cursorSubscriberCount,
		NumberExpression<Long> subscriberCount
	) {
		if (sortDirection.equals(SortDirection.DESCENDING)) {
			return subscriberCount.lt(cursorSubscriberCount)
				.or(
					subscriberCount.eq(cursorSubscriberCount)
						.and(playlist.id.lt(idAfter))
				);
		}

		if (sortDirection.equals(SortDirection.ASCENDING)) {
			return subscriberCount.gt(cursorSubscriberCount)
				.or(
					subscriberCount.eq(cursorSubscriberCount)
						.and(playlist.id.gt(idAfter))
				);
		}

		throw new PlaylistException(PlaylistErrorCode.INVALID_INPUT);
	}

	private OrderSpecifier<?>[] orderCondition(
		SortDirection sortDirection,
		PlaylistSortBy sortBy,
		NumberExpression<Long> subscriberCount
	) {
		if (sortBy.equals(PlaylistSortBy.updatedAt)) {
			return updateOrder(sortDirection);
		}

		if (sortBy.equals(PlaylistSortBy.subscribeCount)) {
			return subscriberCountOrder(sortDirection, subscriberCount);
		}

		// 정렬 조건에 updatedAt이나 subscriberCount 이외의 것이 입력되었을 경우 예외 발생
		throw new PlaylistException(PlaylistErrorCode.INVALID_INPUT);
	}

	private OrderSpecifier<?>[] updateOrder(SortDirection sortDirection) {
		boolean descending = isDescending(sortDirection);

		return new OrderSpecifier<?>[] {
			descending
				? playlist.updatedAt.desc()
				: playlist.updatedAt.asc(),
			descending
				? playlist.id.desc()
				: playlist.id.asc()
		};
	}

	private OrderSpecifier<?>[] subscriberCountOrder(
		SortDirection sortDirection,
		NumberExpression<Long> subscriberCount
	) {
		boolean descending = isDescending(sortDirection);

		return new OrderSpecifier<?>[] {
			descending
				? subscriberCount.desc()
				: subscriberCount.asc(),
			descending
				? playlist.id.desc()
				: playlist.id.asc()
		};
	}

	private boolean isDescending(SortDirection sortDirection) {
		return sortDirection.equals(SortDirection.DESCENDING);
	}

	// 조건에 따른 전체 데이터 개수 조회
	private Long getTotalCount(PlaylistSearchRequest request) {
		return jpaQueryFactory
			.select(playlist.count())
			.from(playlist)
			.where(
				// 논리 삭제되지 않은 플레이리스트 (playlists.deleted_at IS NULL)
				playlist.deletedAt.isNull(),
				// normalizedKeyword를 포함한 title or description (playlists.title LIKE %normalizedKeyword%)
				keywordLike(request.normalizedKeyword()),
				// 입력된 ownerId가 생성한 플레이리스트인지
				ownerIdEq(request.ownerIdEqual()),
				// 입력된 subscriberId가 구독한 플레이리스트인지
				subscriberIdEq(request.subscriberIdEqual())
			)
			.fetchOne();
	}
}

