-- psql only. Explicit single-event scope; default is a dry run and ROLLBACK.
-- Usage: psql service=r4r-maintenance -v event_id=UUID -v confirm=REVIEW -f this-file.sql
-- After preview, use confirm=ERASE-ONE-EVENT AND deliberately replace ROLLBACK with COMMIT
-- in a reviewed private copy. Assess LOPDGDD blocking/legal holds before irreversible deletion.
\set ON_ERROR_STOP on
BEGIN;
SET LOCAL lock_timeout='2s';
SET LOCAL statement_timeout='5s';
SELECT id,origin,event_type,occurred_at,restricted_at FROM assistant_usage WHERE id=:'event_id'::uuid FOR UPDATE;
DELETE FROM assistant_usage WHERE id=:'event_id'::uuid AND :'confirm'='ERASE-ONE-EVENT' AND restricted_at IS NULL;
ROLLBACK;
-- To request restriction first, manually run with a single UUID and verified authority:
-- UPDATE assistant_usage SET restricted_at=now() WHERE id=...;
-- Identifiers can instead be unlinked manually without deleting aggregate metadata:
-- UPDATE assistant_usage SET visitor_id=NULL,session_id=NULL,analytics_consent=NULL WHERE id=...;
-- Minimisation: question=NULL,answer=NULL,audit_result=NULL,history_consent=NULL (scope by verified IDs).
-- Do not execute bulk text searches/exports into shared terminals or application logs.
-- Network minimisation for a verified single event (review the security incident first):
-- UPDATE assistant_usage SET client_ip=NULL,country_code=NULL,security_basis=NULL,network_review_due_at=NULL WHERE id=... AND restricted_at IS NULL;
