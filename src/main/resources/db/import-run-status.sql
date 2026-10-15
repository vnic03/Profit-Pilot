CREATE TABLE IF NOT EXISTS import_run_status (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    payload TEXT NOT NULL
);
