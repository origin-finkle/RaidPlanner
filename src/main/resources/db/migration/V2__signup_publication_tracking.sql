ALTER TABLE raids
    ADD COLUMN signup_message_id BIGINT NULL,
    ADD COLUMN signup_channel_id VARCHAR(255) NULL,
    ADD COLUMN last_signup_published_at DATETIME(6) NULL;

CREATE INDEX IDX_raids_signup_message_id ON raids (signup_message_id);
