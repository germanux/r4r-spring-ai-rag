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
-- Minimise a single reviewed record, preserving its technical aggregate outcome:
-- UPDATE assistant_usage SET question=NULL,answer=NULL,audit_result=NULL,citations=NULL,
--   content_basis=NULL,content_exclusion='manually_minimized',client_ip=NULL,country_code=NULL,
--   network_basis=NULL,view_id=NULL WHERE id=...;
-- Do not export personal text/IPs to shared terminals or application logs.
-- Restricted records require assessment of legal holds before their final erasure.
