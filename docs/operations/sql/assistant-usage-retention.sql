-- Daily maintenance with a restricted DELETE-capable account. No output of user data.
DELETE FROM assistant_usage WHERE occurred_at < now() - interval '30 days';
