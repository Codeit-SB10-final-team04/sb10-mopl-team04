CREATE TABLE users
(
    id                UUID PRIMARY KEY,
    name              VARCHAR(50)              NOT NULL,
    email             VARCHAR(255)             NOT NULL UNIQUE,
    email_type        VARCHAR(20)              NOT NULL,
    password_hash     VARCHAR(255),
    profile_image_url VARCHAR(500),
    role              VARCHAR(50)              NOT NULL,
    is_locked         BOOLEAN                  NOT NULL,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL
);


CREATE TABLE playlists
(
    id          UUID PRIMARY KEY,
    owner_id    UUID                     NOT NULL,
    title       VARCHAR(100)             NOT NULL,
    description TEXT                     NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    deleted_at  TIMESTAMP WITH TIME ZONE,
    CONSTRAINT fk_playlists_owner FOREIGN KEY (owner_id) REFERENCES users (id)
);

CREATE TABLE playlist_subscriptions
(
    id            UUID PRIMARY KEY,
    subscriber_id UUID                     NOT NULL,
    playlist_id   UUID                     NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT fk_playlist_subscriptions_subscriber FOREIGN KEY (subscriber_id) REFERENCES users (id),
    CONSTRAINT fk_playlist_subscriptions_playlist FOREIGN KEY (playlist_id) REFERENCES playlists (id),
    CONSTRAINT uq_playlist_subscriptions UNIQUE (subscriber_id, playlist_id)
);
