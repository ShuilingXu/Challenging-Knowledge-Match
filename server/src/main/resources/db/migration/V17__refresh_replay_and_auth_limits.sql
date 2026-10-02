alter table refresh_tokens add column replacement_ciphertext varchar(256);
create table auth_rate_limits(rate_key varchar(64) primary key, attempts integer not null, expires_at timestamp with time zone not null);
create index idx_auth_rate_expiry on auth_rate_limits(expires_at);
