-- SQLite: Remove is_guild column (derived from guild_id != null)
-- SQLite doesn't support DROP COLUMN directly before 3.35.0, so we need to recreate the table

-- Create new table without is_guild
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
    lang TEXT
);

-- Copy data (is_guild is dropped)
INSERT INTO destinations_new (target_id, channel_id, thread_id, guild_id, eew_alert, eew_prediction, eew_decimation, quake_info, min_intensity, lang)
SELECT target_id, channel_id, thread_id, guild_id, eew_alert, eew_prediction, eew_decimation, quake_info, min_intensity, lang
FROM destinations;

-- Drop old table
DROP TABLE destinations;

-- Rename new table
ALTER TABLE destinations_new RENAME TO destinations;

-- Recreate indexes
CREATE INDEX idx_destinations_channel_id ON destinations(channel_id);
CREATE INDEX idx_destinations_thread_id ON destinations(thread_id);
CREATE INDEX idx_destinations_guild_id ON destinations(guild_id);
CREATE INDEX idx_destinations_delivery_filter ON destinations(eew_alert, eew_prediction, eew_decimation, quake_info, min_intensity);
