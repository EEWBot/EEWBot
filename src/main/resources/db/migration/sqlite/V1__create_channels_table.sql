-- SQLite: INTEGER for booleans
PRAGMA journal_mode=WAL;

-- Main channels table
CREATE TABLE IF NOT EXISTS channels (
    channel_id INTEGER PRIMARY KEY,
    is_guild INTEGER,
    guild_id INTEGER,
    eew_alert INTEGER NOT NULL DEFAULT 0,
    eew_prediction INTEGER NOT NULL DEFAULT 0,
    eew_decimation INTEGER NOT NULL DEFAULT 0,
    quake_info INTEGER NOT NULL DEFAULT 0,
    min_intensity INTEGER NOT NULL DEFAULT 1,
    lang TEXT
);

-- Separate webhook table (1:1 optional relationship)
CREATE TABLE IF NOT EXISTS channel_webhooks (
    channel_id INTEGER PRIMARY KEY REFERENCES channels(channel_id) ON DELETE CASCADE,
    webhook_id INTEGER NOT NULL,
    token TEXT NOT NULL,
    thread_id INTEGER
);

CREATE INDEX IF NOT EXISTS idx_channels_guild_id ON channels(guild_id);
CREATE INDEX IF NOT EXISTS idx_channels_notification ON channels(eew_alert, eew_prediction, quake_info, min_intensity);
CREATE INDEX IF NOT EXISTS idx_channel_webhooks_webhook_id ON channel_webhooks(webhook_id);

-- Data migration tracking table
CREATE TABLE IF NOT EXISTS data_migrations (
    name TEXT PRIMARY KEY,
    applied_at TEXT NOT NULL DEFAULT (datetime('now'))
);
