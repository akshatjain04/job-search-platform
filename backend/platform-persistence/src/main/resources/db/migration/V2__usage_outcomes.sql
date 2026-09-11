ALTER TABLE app.ai_usage ADD COLUMN outcome text NOT NULL DEFAULT 'SUCCESS';
CREATE INDEX oauth_state_expiry ON app.oauth_states(expires_at);
CREATE INDEX extension_code_expiry ON app.extension_codes(expires_at);
