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
    CONSTRAINT fk_playlists_owner FOREIGN KEY (owner_id) REFERENCES users (id) ON DELETE CASCADE
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
    PRIMARY KEY (id)
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

CREATE TABLE IF NOT EXISTS notifications
(
    id              UUID        NOT NULL,
    receiver_id     UUID        NOT NULL,
    source_event_id UUID,
    title           VARCHAR(50) NOT NULL,
    content         TEXT        NOT NULL,
    type            VARCHAR(30) NOT NULL,
    level           VARCHAR(20) NOT NULL,
    read_at         TIMESTAMPTZ NULL,
    created_at      TIMESTAMPTZ NOT NULL,

    CONSTRAINT pk_notifications PRIMARY KEY (id),
    CONSTRAINT fk_notifications_receiver FOREIGN KEY (receiver_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT up_notifications_source_event_receiver UNIQUE (source_event_id, receiver_id)
);
