CREATE TABLE api_rate_limits (
    client_key varchar(255) NOT NULL,
    bucket varchar(50) NOT NULL,
    window_start timestamptz NOT NULL,
    request_count integer NOT NULL CHECK (request_count > 0),
    PRIMARY KEY (client_key, bucket)
);

CREATE INDEX idx_api_rate_limits_window ON api_rate_limits (window_start);

COMMENT ON TABLE api_rate_limits IS
    'Shared fixed-window API throttling state for horizontally scaled application instances.';
