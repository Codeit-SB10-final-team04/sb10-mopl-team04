package com.team04.mopl.content.entity;

import com.team04.mopl.common.entity.BaseEntity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
	name = "content_tags",
	uniqueConstraints = {
		@UniqueConstraint(
			name = "uk_content_tags_content_tag",
			columnNames = {"content_id", "tag_id"}
			)
	}
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ContentTag extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "content_id", nullable = false)
	private Content content;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "tag_id", nullable = false)
	private Tag tag;

	@Builder
	protected ContentTag(Content content, Tag tag) {
		this.content = content;
		this.tag = tag;
	}
}
