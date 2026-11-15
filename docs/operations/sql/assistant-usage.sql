-- Run with an internal read-only database account; never expose via HTTP.
SELECT origin, event_type, count(*) FROM assistant_usage GROUP BY origin,event_type;
SELECT date_trunc('day', occurred_at AT TIME ZONE 'UTC') AS day, origin, count(*)
FROM assistant_usage WHERE event_type='QUERY' GROUP BY day,origin ORDER BY day DESC;
SELECT origin, outcome, count(*), avg(duration_ms) AS mean_ms,
 percentile_cont(0.5) WITHIN GROUP (ORDER BY duration_ms) AS p50_ms,
 percentile_cont(0.95) WITHIN GROUP (ORDER BY duration_ms) AS p95_ms
FROM assistant_usage WHERE event_type='QUERY' GROUP BY origin,outcome;
-- No questions are stored, so FAQ analysis is intentionally unavailable.
-- QUERY / VIEW is an interaction ratio, NOT conversion of unique people:
-- reloads count as views, multiple questions count separately, scripts can be blocked.
