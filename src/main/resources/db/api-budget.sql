CREATE TABLE IF NOT EXISTS api_budget_state (
    provider VARCHAR(40) PRIMARY KEY,
    request_limit INTEGER NOT NULL
);

ALTER TABLE api_budget_state
    DROP CONSTRAINT IF EXISTS api_budget_state_request_limit_check;

ALTER TABLE api_budget_state
    ADD CONSTRAINT api_budget_state_request_limit_check
    CHECK (
        request_limit >= 1
        AND (
            (provider = 'alpha-vantage' AND request_limit <= 25)
            OR
            (provider = 'marketaux' AND request_limit <= 100)
        )
    );

CREATE TABLE IF NOT EXISTS api_request_reservations (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    provider VARCHAR(40) NOT NULL
        REFERENCES api_budget_state(provider),
    symbol VARCHAR(10) NOT NULL,
    reserved_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX IF NOT EXISTS api_request_reservations_window_idx
    ON api_request_reservations (provider, reserved_at);

INSERT INTO api_budget_state (provider, request_limit)
VALUES ('alpha-vantage', 25)
ON CONFLICT (provider) DO NOTHING;

INSERT INTO api_budget_state (provider, request_limit)
VALUES ('marketaux', 80)
ON CONFLICT (provider) DO NOTHING;
