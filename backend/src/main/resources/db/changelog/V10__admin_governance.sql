-- V10__admin_governance.sql — dozor administrátora: audit log a blokace účtů
ALTER TABLE users ADD COLUMN IF NOT EXISTS blocked_at TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN IF NOT EXISTS block_reason TEXT;

-- Audit log byl dosud jen zapisovaný; indexy pro čtení s filtrováním a stránkováním.
CREATE INDEX IF NOT EXISTS audit_log_action_idx ON audit_log(action, id DESC);
CREATE INDEX IF NOT EXISTS audit_log_created_idx ON audit_log(created_at DESC);
CREATE INDEX IF NOT EXISTS audit_log_target_idx ON audit_log(target_type, target_id);

CREATE INDEX IF NOT EXISTS users_created_idx ON users(id DESC);