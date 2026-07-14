package com.team04.mopl.content.repository.qdsl;

import static com.team04.mopl.content.entity.QContent.content;
import static com.team04.mopl.content.entity.QContentTag.contentTag;
import static com.team04.mopl.content.entity.QTag.tag;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.team04.mopl.content.dto.request.ContentPageRequest;
import com.team04.mopl.content.entity.Content;
import com.team04.mopl.content.entity.ContentType;
import com.team04.mopl.content.exception.ContentErrorCode;
import com.team04.mopl.content.exception.ContentException;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class ContentQdslRepositoryImpl implements ContentQdslRepository {

	private final JPAQueryFactory jpaQueryFactory;

	@Override
	public List<Content> findContents(ContentPageRequest req) {
		int limit = req.limit() != null ? req.limit() : 20;
		boolean isDesc = !"ASCENDING".equalsIgnoreCase(req.sortDirection());
		String sortBy = req.sortBy() != null ? req.sortBy() : "watcherCount";

		// cursor와 idAfter는 반드시 함께 전달되어야 함
		boolean hasCursor = req.cursor() != null;
		boolean hasIdAfter = req.idAfter() != null;
		if (hasCursor != hasIdAfter) {
			throw new ContentException(ContentErrorCode.INVALID_CURSOR_PAIR);
		}

		BooleanBuilder where = buildBaseWhere(req);

		if (hasCursor) {
			where.and(buildCursorCondition(sortBy, req.cursor(), req.idAfter(), isDesc));
		}

		return jpaQueryFactory
			.selectFrom(content)
			.where(where)
			.orderBy(buildOrder(sortBy, isDesc), content.id.asc())
			.limit(limit + 1L)
			.fetch();
	}

	@Override
	public long countContents(ContentPageRequest req) {
		Long count = jpaQueryFactory
			.select(content.count())
			.from(content)
			.where(buildBaseWhere(req))
			.fetchOne();
		return count != null ? count : 0L;
	}

	// 커서 제외한 공통 필터 조건
	private BooleanBuilder buildBaseWhere(ContentPageRequest req) {
		BooleanBuilder where = new BooleanBuilder(content.deletedAt.isNull());

		if (req.typeEqual() != null) {
			where.and(content.type.eq(ContentType.valueOf(req.typeEqual())));
		}
		if (req.keywordLike() != null && !req.keywordLike().isBlank()) {
			where.and(content.title.containsIgnoreCase(req.keywordLike()));
		}
		if (req.tagsIn() != null && !req.tagsIn().isEmpty()) {
			where.and(content.id.in(
				JPAExpressions.select(contentTag.content.id)
					.from(contentTag)
					.join(contentTag.tag, tag)
					.where(tag.name.in(req.tagsIn()))
			));
		}

		return where;
	}

	// 커서 조건: (정렬값 비교) OR (정렬값 동일 AND id 비교)
	private BooleanExpression buildCursorCondition(String sortBy, String cursor, UUID idAfter, boolean isDesc) {
		return switch (sortBy) {
			case "rate" -> {
				BigDecimal cursorVal = new BigDecimal(cursor);
				yield isDesc
					? content.averageRating.lt(cursorVal)
						.or(content.averageRating.eq(cursorVal).and(content.id.gt(idAfter)))
					: content.averageRating.gt(cursorVal)
						.or(content.averageRating.eq(cursorVal).and(content.id.gt(idAfter)));
			}
			case "createdAt" -> {
				Instant cursorVal;
				try {
					cursorVal = Instant.parse(cursor);
				} catch (Exception e) {
					throw new ContentException(ContentErrorCode.INVALID_CURSOR);
				}
				yield isDesc
					? content.createdAt.lt(cursorVal)
						.or(content.createdAt.eq(cursorVal).and(content.id.gt(idAfter)))
					: content.createdAt.gt(cursorVal)
						.or(content.createdAt.eq(cursorVal).and(content.id.gt(idAfter)));
			}
			default -> { // watcherCount
				long cursorVal = Long.parseLong(cursor);
				yield isDesc
					? content.watcherCount.lt(cursorVal)
						.or(content.watcherCount.eq(cursorVal).and(content.id.gt(idAfter)))
					: content.watcherCount.gt(cursorVal)
						.or(content.watcherCount.eq(cursorVal).and(content.id.gt(idAfter)));
			}
		};
	}

	private OrderSpecifier<?> buildOrder(String sortBy, boolean isDesc) {
		return switch (sortBy) {
			case "rate" -> isDesc ? content.averageRating.desc() : content.averageRating.asc();
			case "createdAt" -> isDesc ? content.createdAt.desc() : content.createdAt.asc();
			default -> isDesc ? content.watcherCount.desc() : content.watcherCount.asc();
		};
	}
}
