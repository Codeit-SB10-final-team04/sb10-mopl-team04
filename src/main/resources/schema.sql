-- ----------------------------------------------------------------
-- ENUM 타입 정의
-- ----------------------------------------------------------------
CREATE TYPE content_type AS ENUM ('movie', 'tvSeries', 'sport');
CREATE TYPE collection_source AS ENUM ('TMDB', 'SPORTS_DB', 'MANUAL');
CREATE TYPE email_type AS ENUM ('REAL', 'VIRTUAL');
CREATE TYPE user_role AS ENUM ('ADMIN', 'USER');
CREATE TYPE social_provider AS ENUM ('GOOGLE', 'KAKAO');
-- ================================================================
-- 1. 독립 테이블
-- ================================================================

CREATE TABLE users (
                       id                  UUID            NOT NULL,
                       name                VARCHAR(50)     NOT NULL,
                       email               VARCHAR(255)    NOT NULL,
                       email_type          email_type      NOT NULL DEFAULT 'REAL',
                       password_hash       VARCHAR(255)    NULL,
                       profile_image_url   VARCHAR(500)    NULL,
                       role                VARCHAR(50)     NOT NULL DEFAULT 'USER',
                       is_locked           BOOLEAN         NOT NULL DEFAULT false,
                       created_at          TIMESTAMPTZ     NOT NULL,
                       updated_at          TIMESTAMPTZ     NOT NULL,

                       CONSTRAINT pk_users PRIMARY KEY (id),
                       CONSTRAINT uq_users_email UNIQUE (email)
);

CREATE TABLE tags (
                      id          UUID            NOT NULL,
                      name        VARCHAR(100)    NOT NULL,
                      created_at  TIMESTAMPTZ     NOT NULL DEFAULT NOW(),

                      CONSTRAINT pk_tags PRIMARY KEY (id),
                      CONSTRAINT uq_tags_name UNIQUE (name)
);

CREATE TABLE contents (
                          id              UUID                NOT NULL,
                          external_id     VARCHAR(100)        NULL,
                          source          collection_source   NOT NULL DEFAULT 'MANUAL',
                          title           VARCHAR(200)        NOT NULL,
                          type            content_type        NOT NULL,
                          description     TEXT                NOT NULL,
                          thumbnail_url   VARCHAR(500)        NOT NULL,
                          average_rating  DECIMAL(3,2)        NOT NULL DEFAULT 0.00,
                          review_count    BIGINT              NOT NULL DEFAULT 0,
                          watcher_count   BIGINT              NOT NULL DEFAULT 0,
                          created_at      TIMESTAMPTZ         NOT NULL DEFAULT NOW(),
                          updated_at      TIMESTAMPTZ         NOT NULL,
                          deleted_at      TIMESTAMPTZ         NULL,

                          CONSTRAINT pk_contents PRIMARY KEY (id),
                          CONSTRAINT uq_contents_external_id_source UNIQUE (external_id, source)
);

-- 콘텐츠 목록 조회 성능 인덱스 (정렬 기준별 Partial Index)
CREATE INDEX idx_contents_active_watcher
    ON contents (watcher_count DESC, id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_contents_active_rating
    ON contents (average_rating DESC, id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_contents_active_created
    ON contents (created_at DESC, id)
    WHERE deleted_at IS NULL;

CREATE TABLE conversations (
                               id          UUID        NOT NULL,
                               created_at  TIMESTAMPTZ NOT NULL,
                               updated_at  TIMESTAMPTZ NOT NULL,

                               CONSTRAINT pk_conversations PRIMARY KEY (id)
);


-- ================================================================
-- 2. users 참조 테이블
-- ================================================================

CREATE TABLE follows (
                         id          UUID        NOT NULL,
                         followee_id UUID        NOT NULL,
                         follower_id UUID        NOT NULL,
                         created_at  TIMESTAMPTZ NOT NULL,

                         CONSTRAINT pk_follows PRIMARY KEY (id),
                         CONSTRAINT fk_follows_followee FOREIGN KEY (followee_id) REFERENCES users (id) ON DELETE CASCADE,
                         CONSTRAINT fk_follows_follower FOREIGN KEY (follower_id) REFERENCES users (id) ON DELETE CASCADE,

                         CONSTRAINT uk_follow_followee_follower UNIQUE (followee_id, follower_id)
);

CREATE TABLE temporary_passwords (
                                     user_id         UUID            NOT NULL,
                                     password_hash   VARCHAR(255)    NOT NULL,
                                     expires_at      TIMESTAMPTZ     NOT NULL,
                                     created_at      TIMESTAMPTZ     NOT NULL,

                                     CONSTRAINT pk_temporary_passwords PRIMARY KEY (user_id),
                                     CONSTRAINT fk_temporary_passwords_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE social_accounts (
                                 id               UUID            NOT NULL,
                                 user_id          UUID            NOT NULL,
                                 social_provider         VARCHAR(20)     NOT NULL,
                                 provider_user_id VARCHAR(255)    NOT NULL,
                                 provider_email   VARCHAR(255)    NULL,
                                 created_at       TIMESTAMPTZ     NOT NULL,

                                 CONSTRAINT pk_social_accounts PRIMARY KEY (id),
                                 CONSTRAINT fk_social_accounts_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
                                 CONSTRAINT uq_social_accounts_provider UNIQUE (social_provider, provider_user_id)
);

CREATE TABLE notifications (
                               id              UUID            NOT NULL,
                               receiver_id     UUID            NOT NULL,
                               source_event_id UUID,
                               title           VARCHAR(50)     NOT NULL,
                               content         TEXT            NOT NULL,
                               type            VARCHAR(30)     NOT NULL,
                               level           VARCHAR(20)     NOT NULL,
                               read_at         TIMESTAMPTZ     NULL,
                               created_at      TIMESTAMPTZ     NOT NULL,

                               CONSTRAINT pk_notifications PRIMARY KEY (id),
                               CONSTRAINT fk_notifications_receiver FOREIGN KEY (receiver_id) REFERENCES users (id) ON DELETE CASCADE,
                               CONSTRAINT up_notifications_source_event_receiver UNIQUE (source_event_id, receiver_id)
);

CREATE TABLE playlists (
                           id          UUID            NOT NULL,
                           owner_id    UUID            NOT NULL,
                           title       VARCHAR(100)    NOT NULL,
                           description TEXT            NOT NULL,
                           created_at  TIMESTAMPTZ     NOT NULL,
                           updated_at  TIMESTAMPTZ     NOT NULL,
                           deleted_at  TIMESTAMPTZ     NULL,

                           CONSTRAINT pk_playlists PRIMARY KEY (id),
                           CONSTRAINT fk_playlists_owner FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE CASCADE
);


-- ================================================================
-- 3. contents 참조 테이블
-- ================================================================

CREATE TABLE content_tags (
                              id          UUID        NOT NULL,
                              content_id  UUID        NOT NULL,
                              tag_id      UUID        NOT NULL,
                              created_at  TIMESTAMPTZ NOT NULL,

                              CONSTRAINT pk_content_tags PRIMARY KEY (id),
                              CONSTRAINT uk_content_tags_content_tag UNIQUE (content_id, tag_id),
                              CONSTRAINT fk_content_tags_content FOREIGN KEY (content_id) REFERENCES contents (id) ON DELETE CASCADE,
                              CONSTRAINT fk_content_tags_tag FOREIGN KEY (tag_id) REFERENCES tags (id) ON DELETE CASCADE
);

CREATE TABLE content_reviews (
                                 id          UUID        NOT NULL,
                                 user_id     UUID        NOT NULL,
                                 content_id  UUID        NOT NULL,
                                 text     TEXT        NOT NULL,
                                 rating      SMALLINT    NOT NULL,
                                 created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                                 updated_at  TIMESTAMPTZ NOT NULL,
                                 deleted_at  TIMESTAMPTZ NULL,

                                 CONSTRAINT pk_content_reviews PRIMARY KEY (id),
                                 CONSTRAINT fk_content_reviews_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
                                 CONSTRAINT fk_content_reviews_content FOREIGN KEY (content_id) REFERENCES contents (id) ON DELETE CASCADE,
                                 CONSTRAINT chk_content_reviews_rating CHECK (rating BETWEEN 1 AND 5)
);

CREATE UNIQUE INDEX uk_content_reviews_user_content_active
    ON content_reviews (user_id, content_id)
    WHERE deleted_at IS NULL;

-- 리뷰 목록 조회 성능 인덱스 (정렬 기준별 Partial Index)
CREATE INDEX idx_reviews_content_created
    ON content_reviews (content_id, created_at DESC, id DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_reviews_content_rating
    ON content_reviews (content_id, rating DESC, id DESC)
    WHERE deleted_at IS NULL;

CREATE TABLE watching_sessions (
                                   id          UUID        NOT NULL,
                                   content_id  UUID        NOT NULL,
                                   watcher_id  UUID        NOT NULL,
                                   created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                                   updated_at  TIMESTAMPTZ NOT NULL,

                                   CONSTRAINT pk_watching_sessions PRIMARY KEY (id),
                                   CONSTRAINT fk_watching_sessions_content FOREIGN KEY (content_id) REFERENCES contents (id) ON DELETE CASCADE,
                                   CONSTRAINT fk_watching_sessions_watcher FOREIGN KEY (watcher_id) REFERENCES users (id) ON DELETE CASCADE
);


-- ================================================================
-- 4. playlists 참조 테이블
-- ================================================================

CREATE TABLE playlist_contents (
                                   id          UUID        NOT NULL,
                                   playlist_id UUID        NOT NULL,
                                   content_id  UUID        NOT NULL,
                                   created_at  TIMESTAMPTZ NOT NULL,

                                   CONSTRAINT pk_playlist_contents PRIMARY KEY (id),
                                   CONSTRAINT fk_playlist_contents_playlist FOREIGN KEY (playlist_id) REFERENCES playlists (id) ON DELETE CASCADE,
                                   CONSTRAINT fk_playlist_contents_content FOREIGN KEY (content_id) REFERENCES contents (id) ON DELETE CASCADE,
                                   CONSTRAINT uq_playlist_contents UNIQUE (playlist_id, content_id)
);

CREATE TABLE playlist_subscriptions (
                                        id            UUID        NOT NULL,
                                        subscriber_id UUID        NOT NULL,
                                        playlist_id   UUID        NOT NULL,
                                        created_at    TIMESTAMPTZ NOT NULL,

                                        CONSTRAINT pk_playlist_subscriptions PRIMARY KEY (id),
                                        CONSTRAINT fk_playlist_subscriptions_subscriber FOREIGN KEY (subscriber_id) REFERENCES users (id) ON DELETE CASCADE,
                                        CONSTRAINT fk_playlist_subscriptions_playlist FOREIGN KEY (playlist_id) REFERENCES playlists (id) ON DELETE CASCADE,
                                        CONSTRAINT uq_playlist_subscriptions UNIQUE (subscriber_id, playlist_id)
);


-- ================================================================
-- 5. conversations 참조 테이블
-- ================================================================

CREATE TABLE conversation_participants (
                                           conversation_id UUID        NOT NULL,
                                           user_id         UUID        NOT NULL,
                                           last_read_at    TIMESTAMPTZ NULL,

                                           CONSTRAINT pk_conversation_participants PRIMARY KEY (conversation_id, user_id),
                                           CONSTRAINT fk_conversation_participants_conversation FOREIGN KEY (conversation_id) REFERENCES conversations (id) ON DELETE CASCADE,
                                           CONSTRAINT fk_conversation_participants_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE TABLE direct_messages (
                                 id              UUID        NOT NULL,
                                 sender_id       UUID        NOT NULL,
                                 receiver_id     UUID        NOT NULL,
                                 conversation_id UUID        NOT NULL,
                                 content         TEXT        NOT NULL,
                                 is_read         BOOLEAN     NOT NULL DEFAULT false,
                                 read_at         TIMESTAMPTZ NULL,
                                 created_at      TIMESTAMPTZ NOT NULL,

                                 CONSTRAINT pk_direct_messages PRIMARY KEY (id),
                                 CONSTRAINT fk_direct_messages_sender FOREIGN KEY (sender_id) REFERENCES users (id) ON DELETE CASCADE,
                                 CONSTRAINT fk_direct_messages_receiver FOREIGN KEY (receiver_id) REFERENCES users (id) ON DELETE CASCADE,
                                 CONSTRAINT fk_direct_messages_conversation FOREIGN KEY (conversation_id) REFERENCES conversations (id) ON DELETE CASCADE
);

-- ================================================================
-- 6. INDEX
-- ================================================================

-- notifications

CREATE INDEX idx_notifications_unread_receiver_created_id
    ON notifications (receiver_id, created_at, id)
    WHERE read_at IS NULL;

CREATE INDEX idx_notifications_read_at_id
    ON notifications (read_at, id)
    WHERE read_at IS NOT NULL;

-- playlists

CREATE INDEX idx_playlists_active_updated_id
    ON playlists (updated_at, id)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_playlists_deleted_at_id
    ON playlists (deleted_at, id)
    WHERE deleted_at IS NOT NULL;

-- playlist_subscriptions

CREATE INDEX idx_playlist_subscriptions_playlist_subscriber
    ON playlist_subscriptions (playlist_id, subscriber_id);

-- users

CREATE INDEX idx_users_name_id
    ON users (name, id);

CREATE INDEX idx_users_email_id
    ON users (email, id);

CREATE INDEX idx_users_created_at_id
    ON users (created_at, id);

CREATE INDEX idx_users_is_locked_id
    ON users (is_locked, id);

CREATE INDEX idx_users_role_id
    ON users (role, id);

-- direct_messages

CREATE INDEX idx_direct_messages_id
    ON direct_messages (conversation_id, created_at, id);

-- conversation

CREATE INDEX idx_conversation_participant_user_id
    ON conversation_participants (user_id);

CREATE INDEX idx_conversations_created_at_id
    ON conversations (created_at, id);
