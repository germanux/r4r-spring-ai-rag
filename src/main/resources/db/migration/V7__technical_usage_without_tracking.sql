-- V5/V6 remain immutable, including for installations that already applied them.
ALTER TABLE assistant_usage ADD COLUMN content_basis VARCHAR(32),
 ADD COLUMN content_exclusion VARCHAR(32), ADD COLUMN view_id UUID,
 ADD COLUMN citations JSONB, ADD COLUMN error_code VARCHAR(32);
-- Preserve the provenance of any previously collected text; do not relabel old consent as LI.
UPDATE assistant_usage SET content_basis='legacy-consent-v1' WHERE history_consent IS NOT NULL;
ALTER TABLE assistant_usage DROP CONSTRAINT assistant_history_consent,
 DROP CONSTRAINT assistant_visitor_consent;
ALTER TABLE assistant_usage RENAME COLUMN security_basis TO network_basis;
ALTER TABLE assistant_usage RENAME COLUMN consent_at TO basis_recorded_at;
ALTER TABLE assistant_usage DROP COLUMN visitor_id, DROP COLUMN session_id,
 DROP COLUMN analytics_consent, DROP COLUMN history_consent,
 DROP COLUMN network_review_due_at, DROP COLUMN review_due_at;
ALTER TABLE assistant_usage ADD CONSTRAINT assistant_content_basis
 CHECK ((question IS NULL AND answer IS NULL) OR content_basis IS NOT NULL);
CREATE INDEX assistant_usage_view ON assistant_usage(origin,view_id) WHERE view_id IS NOT NULL;
COMMENT ON COLUMN assistant_usage.view_id IS 'Ephemeral assistant opening; never a persistent visitor identifier';
COMMENT ON COLUMN assistant_usage.network_basis IS 'Separate security IP and technical geographic statistics purposes';
