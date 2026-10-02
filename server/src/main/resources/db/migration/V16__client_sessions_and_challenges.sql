create table client_credentials (
 id uuid primary key, token_hash varchar(64) not null unique, kind varchar(24) not null,
 activity_id uuid not null, subject_id uuid not null, expires_at timestamp with time zone not null
);
create table human_challenges (
 id uuid primary key, answer_hash varchar(64) not null, ip varchar(64) not null, contact_hash varchar(64),
 created_at timestamp with time zone not null, expires_at timestamp with time zone not null, used boolean not null
);
create index idx_human_challenge_ip on human_challenges(ip,created_at);
create index idx_human_challenge_contact on human_challenges(contact_hash,created_at);
