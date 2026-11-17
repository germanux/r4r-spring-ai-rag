-- Operator-only technical analysis. Exclude restricted records, never expose through public HTTP.
SELECT count(*) AS assistant_views FROM assistant_usage WHERE event_type='VIEW' AND restricted_at IS NULL;
SELECT count(*) AS questions FROM assistant_usage WHERE event_type='QUERY' AND restricted_at IS NULL;
SELECT origin,event_type,count(*) FROM assistant_usage WHERE restricted_at IS NULL GROUP BY origin,event_type;
SELECT origin,country_code,count(*) FROM assistant_usage WHERE restricted_at IS NULL GROUP BY origin,country_code;
-- Complete retained case and public citation references, without retrieved document bodies.
SELECT id AS request_id,view_id,occurred_at,origin,question,answer,citations,content_basis,content_exclusion,
 outcome,effective_outcome,audit_result,provider,model,error_code,generation_ms,duration_ms,total_duration_ms
FROM assistant_usage WHERE event_type='QUERY' AND restricted_at IS NULL ORDER BY occurred_at DESC LIMIT 100;
SELECT outcome,effective_outcome,count(*) FROM assistant_usage
WHERE event_type='QUERY' AND restricted_at IS NULL GROUP BY outcome,effective_outcome;
SELECT id,origin,question,answer,audit_result FROM assistant_usage
WHERE event_type='QUERY' AND (outcome='ABSTAINED' OR effective_outcome='ABSTAINED') AND restricted_at IS NULL;
SELECT id,origin,outcome,effective_outcome,audit_result FROM assistant_usage
WHERE audit_completed_at IS NOT NULL AND restricted_at IS NULL AND
 (CASE effective_outcome WHEN 'ANSWERED' THEN 2 WHEN 'PARTIAL' THEN 1 WHEN 'ABSTAINED' THEN 0 END)
 < (CASE outcome WHEN 'ANSWERED' THEN 2 WHEN 'PARTIAL' THEN 1 WHEN 'ABSTAINED' THEN 0 END);
SELECT origin,error_code,count(*) FROM assistant_usage WHERE outcome='ERROR' AND restricted_at IS NULL GROUP BY origin,error_code;
SELECT origin,provider,model,count(*),avg(generation_ms) AS mean_generation_ms,avg(duration_ms) AS mean_rag_ms,
 avg(total_duration_ms) AS mean_including_audit_ms,
 percentile_cont(0.5) WITHIN GROUP (ORDER BY duration_ms) AS p50_ms,
 percentile_cont(0.95) WITHIN GROUP (ORDER BY duration_ms) AS p95_ms
FROM assistant_usage WHERE event_type='QUERY' AND restricted_at IS NULL GROUP BY origin,provider,model;
SELECT origin,period,date_trunc(period,occurred_at AT TIME ZONE 'UTC') AS bucket,count(*)
FROM assistant_usage CROSS JOIN (VALUES ('day'),('week'),('month')) AS periods(period)
WHERE event_type='QUERY' AND restricted_at IS NULL GROUP BY origin,period,bucket ORDER BY bucket;
-- Opening/query correlation is ephemeral; no returning-person statistics or IP-based tracking.
SELECT origin,view_id,count(*) FILTER(WHERE event_type='QUERY') AS questions
FROM assistant_usage WHERE view_id IS NOT NULL AND restricted_at IS NULL GROUP BY origin,view_id;
-- Manual necessity review, without invented expiry defaults. Select cases for documented review.
SELECT id,origin,event_type,occurred_at,content_basis,content_exclusion,restricted_at
FROM assistant_usage ORDER BY occurred_at LIMIT 100;
-- Security investigation only: access to raw IP is separate from geographic aggregates.
SELECT id,occurred_at,origin,client_ip,country_code,network_basis FROM assistant_usage
WHERE restricted_at IS NULL AND client_ip IS NOT NULL ORDER BY occurred_at DESC LIMIT 100;
-- Explain gaps in retained text without collecting the excluded personal material.
SELECT content_exclusion,count(*) FROM assistant_usage
WHERE event_type='QUERY' AND restricted_at IS NULL GROUP BY content_exclusion;

-- Geographic product/quality review: country is independent of full-IP security access.
SELECT origin,country_code,count(*) AS questions,
 count(*) FILTER (WHERE outcome='ABSTAINED') AS abstentions,
 count(*) FILTER (WHERE outcome='ERROR') AS errors,
 avg(total_duration_ms) AS mean_total_ms
FROM assistant_usage WHERE event_type='QUERY' AND restricted_at IS NULL
GROUP BY origin,country_code ORDER BY questions DESC;
-- Operator-only texts by country; deliberately no client_ip in this geographic projection.
SELECT id,occurred_at,origin,country_code,question,answer,model,outcome,effective_outcome,audit_result
FROM assistant_usage WHERE event_type='QUERY' AND country_code IN ('ES','IE')
 AND restricted_at IS NULL ORDER BY occurred_at DESC LIMIT 100;
-- Activation check: missing reliable metadata stays NULL, never a guessed location.
SELECT origin,network_basis,count(*) AS records,
 count(client_ip) AS with_ip,count(country_code) AS with_country,
 count(*) FILTER (WHERE family(client_ip)=4 AND masklen(client_ip)=32) AS full_ipv4,
 count(*) FILTER (WHERE family(client_ip)=6 AND masklen(client_ip)=128) AS full_ipv6
FROM assistant_usage WHERE occurred_at >= now()-interval '1 day'
 AND restricted_at IS NULL GROUP BY origin,network_basis;
