-- PostgreSQL: add checksum/meta columns to data_migrations
ALTER TABLE data_migrations ADD COLUMN IF NOT EXISTS checksum TEXT NOT NULL DEFAULT '';
ALTER TABLE data_migrations ADD COLUMN IF NOT EXISTS meta TEXT;
ALTER TABLE data_migrations ALTER COLUMN applied_at TYPE TIMESTAMPTZ USING applied_at AT TIME ZONE 'UTC';
