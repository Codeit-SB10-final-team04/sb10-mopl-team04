CREATE TABLE users
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

CREATE TABLE playlists
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