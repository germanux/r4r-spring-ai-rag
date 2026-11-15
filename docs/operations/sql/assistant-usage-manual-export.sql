-- Authorised single-record export only. Client stdout; never a public endpoint/server filename.
-- (umask 077; psql -q service=r4r-maintenance -v event_id=UUID -f this-file.sql > /private/case.csv)
\set ON_ERROR_STOP on
COPY (SELECT id,view_id,occurred_at,origin,question,answer,citations,outcome,effective_outcome,
 audit_result,provider,model,generation_ms,duration_ms,total_duration_ms,error_code,content_basis,
 content_exclusion,restricted_at FROM assistant_usage WHERE id=:'event_id'::uuid)
TO STDOUT WITH (FORMAT CSV, HEADER TRUE);
