-- PostgreSQL: Make lang column NOT NULL with default value

-- First, fill NULL values with default
UPDATE destinations SET lang = 'ja_JP' WHERE lang IS NULL;

-- Then add NOT NULL constraint with default
ALTER TABLE destinations ALTER COLUMN lang SET DEFAULT 'ja_JP';
ALTER TABLE destinations ALTER COLUMN lang SET NOT NULL;
