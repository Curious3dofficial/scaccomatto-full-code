CREATE TABLE IF NOT EXISTS username_change_requests (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    new_username VARCHAR(24) NOT NULL,
    new_username_normalized VARCHAR(24) NOT NULL,
    otp_hash VARCHAR(512) NOT NULL,
    attempts INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_username_change_expires_at
    ON username_change_requests(expires_at);
