-- psql operator session. Explicit reference, no public history endpoint.
-- psql service=r4r-maintenance -v event_id=UUID -f this-file.sql
\set ON_ERROR_STOP on
BEGIN READ ONLY;
SELECT id,view_id,occurred_at,origin,event_type,question,answer,citations,outcome,effective_outcome,
 audit_result,provider,model,generation_ms,duration_ms,total_duration_ms,error_code,content_basis,
 content_exclusion,restricted_at FROM assistant_usage WHERE id=:'event_id'::uuid;
ROLLBACK;
-- For an authorised private export, run the same single-reference SELECT with psql \copy
-- into a private CSV (directory mode 0700, file mode 0600). Never export the whole ledger by default.
-- For anonymous historical statistics export aggregate counts only, omit text, IPs, identifiers,
-- and assess sparse groups/reidentification; dropping a name alone does not anonymise a record.
