-- Extend V5 without changing its checksum. No retrieval context or document storage.
ALTER TABLE assistant_usage
 ADD COLUMN client_ip INET,
 ADD COLUMN country_code CHAR(2),
 ADD COLUMN security_basis VARCHAR(32),
 ADD COLUMN network_review_due_at TIMESTAMPTZ,
 ADD COLUMN visitor_id UUID,
 ADD COLUMN session_id UUID,
 ADD COLUMN analytics_consent VARCHAR(32),
 ADD COLUMN history_consent VARCHAR(32),
 ADD COLUMN consent_at TIMESTAMPTZ,
 ADD COLUMN question TEXT,
 ADD COLUMN answer TEXT,
 ADD COLUMN provider VARCHAR(256),
 ADD COLUMN model VARCHAR(256),
 ADD COLUMN generation_ms DOUBLE PRECISION,
 ADD COLUMN audit_id UUID,
 ADD COLUMN audit_result JSONB,
 ADD COLUMN effective_outcome VARCHAR(10),
 ADD COLUMN audit_completed_at TIMESTAMPTZ,
 ADD COLUMN total_duration_ms BIGINT,
 ADD COLUMN review_due_at TIMESTAMPTZ NOT NULL DEFAULT (now() + interval '90 days'),
 ADD COLUMN restricted_at TIMESTAMPTZ;
ALTER TABLE assistant_usage ADD CONSTRAINT assistant_history_consent
 CHECK ((question IS NULL AND answer IS NULL) OR history_consent IS NOT NULL);
ALTER TABLE assistant_usage ADD CONSTRAINT assistant_visitor_consent
 CHECK ((visitor_id IS NULL AND session_id IS NULL) OR analytics_consent IS NOT NULL);
ALTER TABLE assistant_usage ADD CONSTRAINT assistant_effective_status
 CHECK (effective_outcome IS NULL OR effective_outcome IN ('ANSWERED','PARTIAL','ABSTAINED','ERROR'));
CREATE UNIQUE INDEX assistant_usage_audit_id ON assistant_usage(audit_id) WHERE audit_id IS NOT NULL;
CREATE INDEX assistant_usage_visitors ON assistant_usage(origin,visitor_id,occurred_at) WHERE visitor_id IS NOT NULL;
CREATE INDEX assistant_usage_review ON assistant_usage(review_due_at);
REVOKE ALL ON assistant_usage FROM PUBLIC;
ALTER TABLE assistant_usage ADD CONSTRAINT assistant_network_purpose
 CHECK ((client_ip IS NULL AND country_code IS NULL) OR security_basis IS NOT NULL);
ALTER TABLE assistant_usage ADD CONSTRAINT assistant_country_code CHECK (country_code IS NULL OR country_code ~ '^[A-Z]{2}$');
CREATE INDEX assistant_usage_network_review ON assistant_usage(network_review_due_at) WHERE client_ip IS NOT NULL;
