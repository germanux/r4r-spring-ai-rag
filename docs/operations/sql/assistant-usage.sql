-- Internal PostgreSQL only. Restricted records are excluded from all routine analysis.
SELECT origin,event_type,count(*) FROM assistant_usage WHERE restricted_at IS NULL GROUP BY origin,event_type;
-- Estimated consenting browser installations, never unique people. Denominator coverage is explicit.
SELECT origin,count(*) FILTER (WHERE event_type='VIEW') AS views,
 count(DISTINCT visitor_id) FILTER (WHERE event_type='VIEW') AS consenting_browsers,
 count(*) FILTER (WHERE event_type='VIEW' AND visitor_id IS NULL) AS untracked_views
FROM assistant_usage WHERE restricted_at IS NULL GROUP BY origin;
-- Mutually exclusive daily cohorts: new that day vs observed on earlier days.
WITH visits AS (
 SELECT origin,visitor_id,occurred_at,
 min(occurred_at) OVER (PARTITION BY origin,visitor_id) AS first_seen
 FROM assistant_usage WHERE restricted_at IS NULL AND event_type='VIEW' AND visitor_id IS NOT NULL
), daily AS (
 SELECT origin,visitor_id,date_trunc('day',occurred_at AT TIME ZONE 'UTC') AS day,
 min(first_seen AT TIME ZONE 'UTC') AS first_seen FROM visits GROUP BY origin,visitor_id,day
) SELECT origin,day,
 count(*) FILTER (WHERE date_trunc('day',first_seen)=day) AS new_browsers,
 count(*) FILTER (WHERE date_trunc('day',first_seen)<day) AS returning_browsers
FROM daily GROUP BY origin,day ORDER BY day;
SELECT origin,visitor_id,count(*) AS queries FROM assistant_usage
WHERE event_type='QUERY' AND visitor_id IS NOT NULL AND restricted_at IS NULL GROUP BY origin,visitor_id;
-- Daily, weekly and monthly evolution in UTC.
SELECT origin,period,date_trunc(period,occurred_at AT TIME ZONE 'UTC') AS bucket,count(*)
FROM assistant_usage CROSS JOIN (VALUES ('day'),('week'),('month')) AS periods(period)
WHERE event_type='QUERY' AND restricted_at IS NULL GROUP BY origin,period,bucket ORDER BY bucket;
SELECT origin,outcome,effective_outcome,count(*),avg(duration_ms),avg(generation_ms),avg(total_duration_ms),
 percentile_cont(0.5) WITHIN GROUP (ORDER BY duration_ms) AS p50_ms,
 percentile_cont(0.95) WITHIN GROUP (ORDER BY duration_ms) AS p95_ms
FROM assistant_usage WHERE event_type='QUERY' AND restricted_at IS NULL GROUP BY origin,outcome,effective_outcome;
SELECT origin,audit_result->>'conclusive' AS conclusive,audit_result->>'effectiveStatus' AS audited_status,count(*)
FROM assistant_usage WHERE audit_result IS NOT NULL AND restricted_at IS NULL GROUP BY origin,conclusive,audited_status;
-- Access-controlled quality review; never feed these rows into public endpoints or routine logs.
SELECT id,occurred_at,origin,question,answer,outcome,effective_outcome,audit_result,provider,model
FROM assistant_usage WHERE event_type='QUERY' AND history_consent IS NOT NULL AND restricted_at IS NULL
ORDER BY occurred_at DESC LIMIT 100;
-- Monthly operator review: metadata only until individual access is justified.
SELECT id,origin,event_type,occurred_at,review_due_at,history_consent IS NOT NULL AS has_text,
 visitor_id IS NOT NULL AS has_tracking FROM assistant_usage WHERE review_due_at <= now() ORDER BY review_due_at;
-- Security investigation only: no IP-based recurrent visitor analytics.
SELECT id,occurred_at,origin,event_type,client_ip,country_code,security_basis,network_review_due_at
FROM assistant_usage WHERE restricted_at IS NULL AND client_ip IS NOT NULL
ORDER BY occurred_at DESC LIMIT 100;
SELECT origin,country_code,count(*) FROM assistant_usage
WHERE analytics_consent IS NOT NULL AND restricted_at IS NULL GROUP BY origin,country_code;
-- Weekly manual network minimisation review, not a deletion job.
SELECT id,origin,event_type,network_review_due_at FROM assistant_usage
WHERE client_ip IS NOT NULL AND network_review_due_at <= now() AND restricted_at IS NULL;
