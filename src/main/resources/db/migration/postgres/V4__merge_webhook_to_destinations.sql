-- PostgreSQL: Merge destination_webhooks into destinations table
-- Add webhook_url column containing full URL (including ?thread_id= if applicable)

-- Step 1: Add webhook_url column
ALTER TABLE destinations ADD COLUMN webhook_url TEXT;

-- Step 2: Update destinations with webhook URLs from destination_webhooks
-- If thread_id is present, append ?thread_id= to the URL
UPDATE destinations d
SET webhook_url =
    CASE
        WHEN d.thread_id IS NOT NULL THEN
            'https://discord.com/api/webhooks/' || w.webhook_id || '/' || w.token || '?thread_id=' || d.thread_id
        ELSE
            'https://discord.com/api/webhooks/' || w.webhook_id || '/' || w.token
    END
FROM destination_webhooks w
WHERE w.target_id = d.target_id;

-- Step 3: Drop old webhook table and its index
DROP TABLE destination_webhooks;

-- Step 4: Create index on webhook_url for efficient lookups
CREATE INDEX idx_destinations_webhook_url ON destinations(webhook_url);
