-- SQLite: add checksum/meta columns to data_migrations
ALTER TABLE data_migrations ADD COLUMN checksum TEXT NOT NULL DEFAULT '';
ALTER TABLE data_migrations ADD COLUMN meta TEXT;
