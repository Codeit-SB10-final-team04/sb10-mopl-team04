CREATE TABLE IF NOT EXISTS users (
                                     id                UUID PRIMARY KEY,
                                     name              VARCHAR(50)              NOT NULL,
                                     email             VARCHAR(255)             NOT NULL UNIQUE,
                                     email_type        VARCHAR(20)              NOT NULL,
                                     password_hash     VARCHAR(255),
                                     profile_image_url VARCHAR(500),
                                     role              VARCHAR(50)              NOT NULL,
                                     is_locked         BOOLEAN                  NOT NULL DEFAULT false,
                                     created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
                                     updated_at        TIMESTAMP WITH TIME ZONE NOT NULL
   );

CREATE TABLE IF NOT EXISTS conversations (
                                             id          UUID PRIMARY KEY,
                                             created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
                                             updated_at  TIMESTAMP WITH TIME ZONE NOT NULL
    );

CREATE TABLE IF NOT EXISTS conversation_participants (
                                                         conversation_id UUID                     NOT NULL,
                                                         user_id         UUID                     NOT NULL,
                                                         last_read_at    TIMESTAMP WITH TIME ZONE,
                                                         CONSTRAINT pk_conversation_participants PRIMARY KEY (conversation_id, user_id),
    CONSTRAINT fk_cp_conversation FOREIGN KEY (conversation_id) REFERENCES conversations (id) ON DELETE CASCADE,
    CONSTRAINT fk_cp_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
    );

CREATE TABLE IF NOT EXISTS direct_messages (
                                               id              UUID PRIMARY KEY,
                                               sender_id       UUID                     NOT NULL,
                                               receiver_id     UUID                     NOT NULL,
                                               conversation_id UUID                     NOT NULL,
                                               content         TEXT                     NOT NULL,
                                               is_read         BOOLEAN                  NOT NULL DEFAULT false,
                                               read_at         TIMESTAMP WITH TIME ZONE,
                                               created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
                                               CONSTRAINT fk_dm_sender FOREIGN KEY (sender_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_dm_receiver FOREIGN KEY (receiver_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_dm_conversation FOREIGN KEY (conversation_id) REFERENCES conversations (id) ON DELETE CASCADE
    );