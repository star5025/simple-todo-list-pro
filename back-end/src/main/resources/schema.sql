CREATE TABLE IF NOT EXISTS task_schedule (
    task_id INT NOT NULL PRIMARY KEY,
    repeat_rule VARCHAR(16) NOT NULL DEFAULT 'NONE',
    revision BIGINT NOT NULL DEFAULT 1,
    ack_revision BIGINT NOT NULL DEFAULT 0,
    successor_task_id INT NULL,
    repeat_day INT NULL
);
