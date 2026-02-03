-- PostgreSQL: Remove is_guild column (derived from guild_id != null)

-- Drop is_guild column
ALTER TABLE destinations DROP COLUMN is_guild;

-- Add index on guild_id for filtering
CREATE INDEX idx_destinations_guild_id ON destinations(guild_id);
