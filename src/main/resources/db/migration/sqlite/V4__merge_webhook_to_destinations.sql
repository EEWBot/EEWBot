-- SQLite: Merge destination_webhooks into destinations table
-- Add webhook_url column containing full URL (including ?thread_id= if applicable)

-- Step 1: Create new table with webhook_url column
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
    lang TEXT,
    webhook_url TEXT
);

-- Step 2: Copy data from destinations, joining with destination_webhooks to build webhook_url
-- If thread_id is present, append ?thread_id= to the URL
INSERT INTO destinations_new (target_id, channel_id, thread_id, guild_id, eew_alert, eew_prediction, eew_decimation, quake_info, min_intensity, lang, webhook_url)
SELECT
    d.target_id,
    d.channel_id,
    d.thread_id,
    d.guild_id,
    d.eew_alert,
    d.eew_prediction,
    d.eew_decimation,
    d.quake_info,
    d.min_intensity,
    d.lang,
    CASE
        WHEN w.webhook_id IS NOT NULL AND d.thread_id IS NOT NULL THEN
            'https://discord.com/api/webhooks/' || w.webhook_id || '/' || w.token || '?thread_id=' || d.thread_id
        WHEN w.webhook_id IS NOT NULL THEN
            'https://discord.com/api/webhooks/' || w.webhook_id || '/' || w.token
        ELSE NULL
    END
FROM destinations d
LEFT JOIN destination_webhooks w ON d.target_id = w.target_id;

-- Step 3: Drop old tables
DROP TABLE destination_webhooks;
DROP TABLE destinations;

-- Step 4: Rename new table
ALTER TABLE destinations_new RENAME TO destinations;

-- Step 5: Recreate indexes
CREATE INDEX idx_destinations_channel_id ON destinations(channel_id);
CREATE INDEX idx_destinations_thread_id ON destinations(thread_id);
CREATE INDEX idx_destinations_guild_id ON destinations(guild_id);
CREATE INDEX idx_destinations_delivery_filter ON destinations(eew_alert, eew_prediction, eew_decimation, quake_info, min_intensity);
CREATE INDEX idx_destinations_webhook_url ON destinations(webhook_url);
