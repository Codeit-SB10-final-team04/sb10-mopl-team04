package com.team04.mopl.content.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.team04.mopl.content.entity.ContentTag;

public interface ContentTagRepository extends JpaRepository<ContentTag, UUID> {
}
