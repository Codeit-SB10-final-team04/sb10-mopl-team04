-- 통합 테스트용 PostgreSQL 스키마
-- Hibernate ddl-auto=none + spring.sql.init으로 로드
-- ENUM은 모두 VARCHAR로 처리 (columnDefinition="email_type" 우회)

CREATE TABLE IF NOT EXISTS users
(
    id                UUID                     NOT NULL,
    name              VARCHAR(50)              NOT NULL,
    email             VARCHAR(255)             NOT NULL UNIQUE,
    email_type        VARCHAR(20)              NOT NULL,
    password_hash     VARCHAR(255),
    profile_image_url VARCHAR(500),
    role              VARCHAR(50)              NOT NULL,
    is_locked         BOOLEAN                  NOT NULL,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS temporary_passwords
(
    user_id       UUID                     NOT NULL,
    password_hash VARCHAR(255)             NOT NULL,
    expires_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (user_id),
    CONSTRAINT fk_temp_passwords_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE IF NOT EXISTS social_accounts
(
    id               UUID                     NOT NULL,
    user_id          UUID                     NOT NULL,
    social_provider  VARCHAR(20)              NOT NULL,
    provider_user_id VARCHAR(255)             NOT NULL,
    provider_email   VARCHAR(255),
    created_at       TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_social_accounts_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT uq_social_accounts_provider UNIQUE (social_provider, provider_user_id)
);

CREATE TABLE IF NOT EXISTS contents
(
    id             UUID                     NOT NULL,
    external_id    VARCHAR(100),
    source         VARCHAR(20)              NOT NULL,
    title          VARCHAR(200)             NOT NULL,
    type           VARCHAR(50)              NOT NULL,
    description    TEXT                     NOT NULL,
    thumbnail_url  VARCHAR(500)             NOT NULL,
    average_rating DECIMAL(3, 2)            NOT NULL,
    review_count   BIGINT                   NOT NULL,
    watcher_count  BIGINT                   NOT NULL,
    deleted_at     TIMESTAMP WITH TIME ZONE,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_contents_external_id_source UNIQUE (external_id, source)
);

CREATE TABLE IF NOT EXISTS tags
(
    id         UUID                     NOT NULL,
    name       VARCHAR(100)             NOT NULL UNIQUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS content_tags
(
    id         UUID                     NOT NULL,
    content_id UUID                     NOT NULL,
    tag_id     UUID                     NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_content_tags_content FOREIGN KEY (content_id) REFERENCES contents (id),
    CONSTRAINT fk_content_tags_tag FOREIGN KEY (tag_id) REFERENCES tags (id),
    CONSTRAINT uk_content_tags_content_tag UNIQUE (content_id, tag_id)
);

CREATE TABLE IF NOT EXISTS content_reviews
(
    id         UUID                     NOT NULL,
    user_id    UUID                     NOT NULL,
    content_id UUID                     NOT NULL,
    text       TEXT                     NOT NULL,
    rating     SMALLINT                 NOT NULL,
    deleted_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_reviews_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_reviews_content FOREIGN KEY (content_id) REFERENCES contents (id)
);

CREATE TABLE IF NOT EXISTS conversations
(
    id         UUID                     NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (id)
);

CREATE TABLE IF NOT EXISTS conversation_participants
(
    conversation_id UUID NOT NULL,
    user_id         UUID NOT NULL,
    last_read_at    TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (conversation_id, user_id),
    CONSTRAINT fk_conv_part_conversation FOREIGN KEY (conversation_id) REFERENCES conversations (id),
    CONSTRAINT fk_conv_part_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE IF NOT EXISTS direct_messages
(
    id              UUID                     NOT NULL,
    sender_id       UUID                     NOT NULL,
    receiver_id     UUID                     NOT NULL,
    conversation_id UUID                     NOT NULL,
    content         TEXT                     NOT NULL,
    is_read         BOOLEAN                  NOT NULL,
    read_at         TIMESTAMP WITH TIME ZONE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_dm_sender FOREIGN KEY (sender_id) REFERENCES users (id),
    CONSTRAINT fk_dm_receiver FOREIGN KEY (receiver_id) REFERENCES users (id),
    CONSTRAINT fk_dm_conversation FOREIGN KEY (conversation_id) REFERENCES conversations (id)
);

CREATE TABLE IF NOT EXISTS follows
(
    id          UUID                     NOT NULL,
    followee_id UUID                     NOT NULL,
    follower_id UUID                     NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_follows_followee FOREIGN KEY (followee_id) REFERENCES users (id),
    CONSTRAINT fk_follows_follower FOREIGN KEY (follower_id) REFERENCES users (id),
    CONSTRAINT uk_follow_followee_follower UNIQUE (followee_id, follower_id)
);

CREATE TABLE IF NOT EXISTS notifications
(
    id          UUID                     NOT NULL,
    receiver_id UUID                     NOT NULL,
    title       VARCHAR(50)              NOT NULL,
    content     TEXT                     NOT NULL,
    type        VARCHAR(30)              NOT NULL,
    level       VARCHAR(20)              NOT NULL,
    read_at     TIMESTAMP WITH TIME ZONE,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_notifications_receiver FOREIGN KEY (receiver_id) REFERENCES users (id)
);

CREATE TABLE IF NOT EXISTS playlists
(
    id          UUID                     NOT NULL,
    owner_id    UUID                     NOT NULL,
    title       VARCHAR(100)             NOT NULL,
    description TEXT                     NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    deleted_at  TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (id),
    CONSTRAINT fk_playlists_owner FOREIGN KEY (owner_id) REFERENCES users (id)
);

CREATE TABLE IF NOT EXISTS playlist_contents
(
    id          UUID                     NOT NULL,
    playlist_id UUID                     NOT NULL,
    content_id  UUID                     NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_pc_playlist FOREIGN KEY (playlist_id) REFERENCES playlists (id),
    CONSTRAINT fk_pc_content FOREIGN KEY (content_id) REFERENCES contents (id),
    CONSTRAINT uq_playlist_contents UNIQUE (playlist_id, content_id)
);

CREATE TABLE IF NOT EXISTS playlist_subscriptions
(
    id            UUID                     NOT NULL,
    subscriber_id UUID                     NOT NULL,
    playlist_id   UUID                     NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_ps_subscriber FOREIGN KEY (subscriber_id) REFERENCES users (id),
    CONSTRAINT fk_ps_playlist FOREIGN KEY (playlist_id) REFERENCES playlists (id),
    CONSTRAINT uq_playlist_subscriptions UNIQUE (subscriber_id, playlist_id)
);
