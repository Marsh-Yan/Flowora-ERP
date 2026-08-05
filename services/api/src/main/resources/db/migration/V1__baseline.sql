CREATE TABLE IF NOT EXISTS flowora_schema_marker (
    id BIGINT NOT NULL PRIMARY KEY,
    version_label VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO flowora_schema_marker (id, version_label)
SELECT 1, 'baseline'
WHERE NOT EXISTS (
    SELECT 1 FROM flowora_schema_marker WHERE id = 1
);
