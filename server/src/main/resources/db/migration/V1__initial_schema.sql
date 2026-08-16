create table activities (
  id uuid not null primary key,
  name varchar(255),
  city varchar(255),
  status varchar(255),
  starts_at timestamp with time zone
);

create table participants (
  id uuid not null primary key,
  activity_id uuid,
  venue varchar(255),
  contact varchar(255),
  name varchar(255),
  organization varchar(255),
  score integer not null,
  registered_at timestamp with time zone,
  constraint uk_participant_activity_venue_contact unique (activity_id, venue, contact)
);

create table questions (
  id uuid not null primary key,
  activity_id uuid,
  type varchar(255),
  title varchar(255),
  options varchar(255),
  answers varchar(255),
  full_score integer not null
);

create table answer_submissions (
  id uuid not null primary key,
  activity_id uuid,
  participant_id uuid,
  question_id uuid,
  idempotency_key varchar(255),
  submitted_answers varchar(255),
  awarded_points integer not null,
  submitted_at timestamp with time zone,
  constraint uk_answer_submission_idempotency_key unique (idempotency_key)
);

create table prize_awards (
  id uuid not null primary key,
  activity_id uuid,
  participant_id uuid,
  prize_name varchar(255),
  delivery_type varchar(255),
  status varchar(255),
  redemption_code varchar(255)
);

create table user_accounts (
  id uuid not null primary key,
  username varchar(120) not null unique,
  display_name varchar(100) not null,
  password_hash varchar(100) not null,
  system_role varchar(40),
  enabled boolean not null,
  created_at timestamp with time zone not null
);

create table activity_memberships (
  id uuid not null primary key,
  activity_id uuid not null,
  user_id uuid not null,
  role varchar(40) not null,
  created_at timestamp with time zone not null,
  constraint uk_activity_membership unique (activity_id, user_id)
);
create index idx_activity_memberships_activity_user on activity_memberships(activity_id, user_id);

create table refresh_tokens (
  id uuid not null primary key,
  user_id uuid not null,
  family_id uuid not null,
  token_hash varchar(64) not null unique,
  expires_at timestamp with time zone not null,
  revoked_at timestamp with time zone,
  replaced_by_hash varchar(64),
  created_at timestamp with time zone not null,
  ip_address varchar(64),
  user_agent varchar(512)
);
create index idx_refresh_tokens_user_id on refresh_tokens(user_id);
create index idx_refresh_tokens_family_id on refresh_tokens(family_id);

create table revoked_access_tokens (
  token_id varchar(64) not null primary key,
  expires_at timestamp with time zone not null,
  user_id uuid not null,
  revoked_at timestamp with time zone not null
);

create table audit_events (
  id uuid not null primary key,
  event_type varchar(80) not null,
  actor_type varchar(40),
  actor_id uuid,
  activity_id uuid,
  success boolean not null,
  ip_address varchar(64),
  user_agent varchar(512),
  details varchar(1000),
  occurred_at timestamp with time zone not null
);
create index idx_audit_events_actor on audit_events(actor_id, occurred_at);
create index idx_audit_events_activity on audit_events(activity_id, occurred_at);
