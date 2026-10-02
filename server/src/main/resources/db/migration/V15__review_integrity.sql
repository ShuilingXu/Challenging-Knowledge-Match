alter table questions alter column title type text;
alter table questions alter column options type text;
alter table questions alter column answers type text;
alter table score_ledgers add column idempotency_key varchar(160);
create unique index idx_score_ledger_idempotency on score_ledgers(activity_id, idempotency_key);
alter table revoked_access_tokens alter column user_id drop not null;
