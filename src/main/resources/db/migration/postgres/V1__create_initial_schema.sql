-- PostgreSQL: Initial schema with destination model

-- Destinations table (channels + threads as unified destinations)
CREATE TABLE destinations
(
    target_id   BIGINT PRIMARY KEY, -- channel_id or thread_id (destination)
    channel_id  BIGINT  NOT NULL,   -- always parent channel ID
    thread_id   BIGINT,             -- thread ID if destination is a thread
    guild_id    BIGINT,
    eew_alert   INTEGER NOT NULL DEFAULT 0,
    eew_prediction INTEGER NOT NULL DEFAULT 0,
    eew_decimation INTEGER NOT NULL DEFAULT 0,
    quake_info  INTEGER NOT NULL DEFAULT 0,
    min_intensity INTEGER NOT NULL DEFAULT 1,
    lang        TEXT    NOT NULL DEFAULT 'ja_jp',
    webhook_url TEXT,               -- full webhook URL (including ?thread_id= if applicable)
    webhook_id  BIGINT
);

-- Config meta table for revision tracking
CREATE TABLE config_meta
(
    id INTEGER PRIMARY KEY CHECK (id = 1),
    channels_revision BIGINT NOT NULL DEFAULT 0
);

-- Data migration tracking table
CREATE TABLE data_migrations
(
    name VARCHAR(255) PRIMARY KEY,
    applied_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    checksum TEXT NOT NULL DEFAULT '',
    meta TEXT
);

-- Indexes
CREATE INDEX idx_destinations_channel_id ON destinations (channel_id);
CREATE INDEX idx_destinations_thread_id ON destinations (thread_id);
CREATE INDEX idx_destinations_guild_id ON destinations (guild_id);
CREATE INDEX idx_destinations_webhook_url ON destinations (webhook_url);
CREATE INDEX idx_destinations_webhook_id ON destinations (webhook_id);

-- Initial config_meta row
INSERT INTO config_meta (id, channels_revision)
VALUES (1, 0);
