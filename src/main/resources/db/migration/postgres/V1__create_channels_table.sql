-- PostgreSQL: Using INTEGER for booleans (0/1) to maintain consistency with SQLite

-- Main channels table
CREATE TABLE IF NOT EXISTS channels (
    channel_id BIGINT PRIMARY KEY,
    is_guild INTEGER,
    guild_id BIGINT,
    eew_alert INTEGER NOT NULL DEFAULT 0,
    eew_prediction INTEGER NOT NULL DEFAULT 0,
    eew_decimation INTEGER NOT NULL DEFAULT 0,
    quake_info INTEGER NOT NULL DEFAULT 0,
    min_intensity INTEGER NOT NULL DEFAULT 1,
    lang VARCHAR(10)
);

-- Separate webhook table (1:1 optional relationship)
CREATE TABLE IF NOT EXISTS channel_webhooks (
    channel_id BIGINT PRIMARY KEY REFERENCES channels(channel_id) ON DELETE CASCADE,
    webhook_id BIGINT NOT NULL,
    token VARCHAR(255) NOT NULL,
    thread_id BIGINT
);

CREATE INDEX IF NOT EXISTS idx_channels_guild_id ON channels(guild_id);
CREATE INDEX IF NOT EXISTS idx_channels_notification ON channels(eew_alert, eew_prediction, quake_info, min_intensity);
CREATE INDEX IF NOT EXISTS idx_channel_webhooks_webhook_id ON channel_webhooks(webhook_id);

-- Data migration tracking table
CREATE TABLE IF NOT EXISTS data_migrations (
    name VARCHAR(255) PRIMARY KEY,
    applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
