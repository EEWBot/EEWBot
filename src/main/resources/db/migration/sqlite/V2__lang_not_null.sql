-- SQLite: Make lang column NOT NULL with default value
-- SQLite requires table recreation for NOT NULL constraint

-- Create new table with NOT NULL constraint
CREATE TABLE destinations_new (
    target_id INTEGER PRIMARY KEY,
    channel_id INTEGER NOT NULL,
    thread_id INTEGER,
    guild_id INTEGER,
    eew_alert INTEGER NOT NULL DEFAULT 0,
    eew_prediction INTEGER NOT NULL DEFAULT 0,
    eew_decimation INTEGER NOT NULL DEFAULT 0,
    quake_info INTEGER NOT NULL DEFAULT 0,
    min_intensity INTEGER NOT NULL DEFAULT 1,
    lang TEXT NOT NULL DEFAULT 'ja_jp',
    webhook_url TEXT
);

-- Copy data, replacing NULL lang with default
INSERT INTO destinations_new
SELECT
    target_id,
    channel_id,
    thread_id,
    guild_id,
    eew_alert,
    eew_prediction,
    eew_decimation,
    quake_info,
    min_intensity,
    COALESCE(lang, 'ja_jp'),
    webhook_url
FROM destinations;

-- Drop old table and rename
DROP TABLE destinations;
ALTER TABLE destinations_new RENAME TO destinations;

-- Recreate indexes
CREATE INDEX idx_destinations_channel_id ON destinations(channel_id);
CREATE INDEX idx_destinations_thread_id ON destinations(thread_id);
CREATE INDEX idx_destinations_guild_id ON destinations(guild_id);
CREATE INDEX idx_destinations_delivery_filter ON destinations(eew_alert, eew_prediction, eew_decimation, quake_info, min_intensity);
CREATE INDEX idx_destinations_webhook_url ON destinations(webhook_url);
