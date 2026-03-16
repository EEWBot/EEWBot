ALTER TABLE destinations
    ADD COLUMN tsunami INTEGER NOT NULL DEFAULT 0;
UPDATE destinations
SET tsunami = 1
WHERE quake_info = 1;
