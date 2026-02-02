-- PostgreSQL: Add config_meta table for revision tracking

CREATE TABLE config_meta (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    channels_revision BIGINT NOT NULL DEFAULT 0
);

INSERT INTO config_meta (id, channels_revision) VALUES (1, 0);
