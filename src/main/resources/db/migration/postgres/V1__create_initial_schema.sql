-- PostgreSQL: Initial schema with destination model

-- Destinations table (channels + threads as unified destinations)
CREATE TABLE destinations (
    target_id BIGINT PRIMARY KEY,      -- channel_id or thread_id (destination)
    channel_id BIGINT NOT NULL,        -- always parent channel ID
    thread_id BIGINT,                  -- thread ID if destination is a thread
    is_guild INTEGER NOT NULL DEFAULT 0,
    guild_id BIGINT,
    eew_alert INTEGER NOT NULL DEFAULT 0,
    eew_prediction INTEGER NOT NULL DEFAULT 0,
    eew_decimation INTEGER NOT NULL DEFAULT 0,
    quake_info INTEGER NOT NULL DEFAULT 0,
    min_intensity INTEGER NOT NULL DEFAULT 1,
    lang TEXT
);

-- Separate webhook table (1:1 optional relationship)
CREATE TABLE destination_webhooks (
    target_id BIGINT PRIMARY KEY REFERENCES destinations(target_id) ON DELETE CASCADE,
    webhook_id BIGINT NOT NULL,
    token TEXT NOT NULL
);

-- Data migration tracking table
CREATE TABLE data_migrations (
    name VARCHAR(255) PRIMARY KEY,
    applied_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    checksum TEXT NOT NULL DEFAULT '',
    meta TEXT
);

-- Indexes
CREATE INDEX idx_destinations_channel_id ON destinations(channel_id);
CREATE INDEX idx_destinations_thread_id ON destinations(thread_id);
CREATE INDEX idx_destinations_delivery_filter ON destinations(eew_alert, eew_prediction, eew_decimation, quake_info, min_intensity);
CREATE INDEX idx_destination_webhooks_webhook_id ON destination_webhooks(webhook_id);
