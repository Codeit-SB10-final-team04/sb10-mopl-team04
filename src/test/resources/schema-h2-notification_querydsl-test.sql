CREATE TABLE IF NOT EXISTS users
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

CREATE TABLE IF NOT EXISTS notifications
(
    id                UUID                     NOT NULL,
    receiver_id       UUID                     NOT NULL,
    source_event_id   UUID,
    title             VARCHAR(50)              NOT NULL,
    content           TEXT                     NOT NULL,
    type              VARCHAR(30)              NOT NULL,
    level             VARCHAR(20)              NOT NULL,
    read_at           TIMESTAMP WITH TIME ZONE NULL,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT pk_notifications PRIMARY KEY (id),
    CONSTRAINT fk_notifications_receiver FOREIGN KEY (receiver_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT up_notifications_source_event_receiver UNIQUE (source_event_id, receiver_id)
);
