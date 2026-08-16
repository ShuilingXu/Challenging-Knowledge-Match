alter table activities add column if not exists description text;
alter table activities add column if not exists ends_at timestamp with time zone;
alter table activities add column if not exists created_at timestamp with time zone;
alter table activities add column if not exists updated_at timestamp with time zone;
alter table activities add column if not exists version bigint default 0 not null;

alter table participants add column if not exists registration_data text;
alter table participants add column if not exists status varchar(24) default 'ACTIVE' not null;
alter table participants add column if not exists last_score_at timestamp with time zone;
alter table participants add column if not exists version bigint default 0 not null;

alter table questions add column if not exists display_order integer default 0 not null;
alter table questions add column if not exists enabled boolean default true not null;
alter table questions add column if not exists media_url varchar(1024);
alter table questions add column if not exists partial_credit_percent integer default 40 not null;
alter table questions add column if not exists version bigint default 0 not null;

alter table answer_submissions add column if not exists status varchar(32) default 'SCORED' not null;
alter table answer_submissions add column if not exists feedback varchar(1000);
alter table answer_submissions add column if not exists graded_at timestamp with time zone;
alter table answer_submissions add column if not exists version bigint default 0 not null;
alter table answer_submissions alter column submitted_answers set data type text;
alter table answer_submissions drop constraint if exists uk_answer_submission_idempotency_key;
alter table answer_submissions add constraint uk_answer_submission_activity_idempotency unique (activity_id, idempotency_key);

alter table prize_awards add column if not exists prize_pool_id uuid;
alter table prize_awards add column if not exists redemption_url varchar(1024);
alter table prize_awards add column if not exists fulfillment_note varchar(600);
alter table prize_awards add column if not exists awarded_at timestamp with time zone;
alter table prize_awards add column if not exists redeemed_at timestamp with time zone;
alter table prize_awards add column if not exists voided_at timestamp with time zone;
alter table prize_awards add column if not exists redeemed_by varchar(255);
alter table prize_awards add column if not exists version bigint default 0 not null;
alter table prize_awards alter column redemption_code set data type varchar(128);
alter table prize_awards add constraint uk_prize_awards_redemption_code unique (redemption_code);

create table venues (
  id uuid not null primary key,
  activity_id uuid not null,
  code varchar(80) not null,
  name varchar(160) not null,
  enabled boolean not null,
  capacity integer,
  created_at timestamp with time zone,
  version bigint default 0 not null,
  constraint uk_venue_activity_code unique (activity_id, code)
);

create table registration_fields (
  id uuid not null primary key,
  activity_id uuid not null,
  field_key varchar(80) not null,
  label varchar(160) not null,
  type varchar(24) not null,
  options text,
  required boolean not null,
  enabled boolean not null,
  display_order integer not null,
  version bigint default 0 not null,
  constraint uk_registration_field_activity_key unique (activity_id, field_key)
);

create table score_ledgers (
  id uuid not null primary key,
  activity_id uuid not null,
  participant_id uuid not null,
  question_id uuid,
  submission_id uuid,
  points integer not null,
  entry_type varchar(48) not null,
  note varchar(400),
  created_at timestamp with time zone not null
);
create index idx_score_ledgers_activity_participant on score_ledgers(activity_id, participant_id);

create table prize_pools (
  id uuid not null primary key,
  activity_id uuid not null,
  code varchar(80) not null,
  name varchar(180) not null,
  purpose varchar(32) not null,
  delivery_type varchar(32) not null,
  description text,
  redemption_url varchar(1024),
  total_quantity integer not null,
  claimed_quantity integer not null,
  min_score integer not null,
  draw_weight integer not null,
  rank_from integer,
  rank_to integer,
  enabled boolean not null,
  created_at timestamp with time zone,
  updated_at timestamp with time zone,
  version bigint default 0 not null,
  constraint uk_prize_pool_activity_code unique (activity_id, code)
);

create table lottery_chances (
  id uuid not null primary key,
  activity_id uuid not null,
  participant_id uuid not null,
  remaining_draws integer not null,
  granted_draws integer not null,
  last_grant_reason varchar(200),
  updated_at timestamp with time zone,
  version bigint default 0 not null,
  constraint uk_lottery_chance_activity_participant unique (activity_id, participant_id)
);

create table lottery_draws (
  id uuid not null primary key,
  activity_id uuid not null,
  participant_id uuid not null,
  prize_pool_id uuid not null,
  prize_award_id uuid not null,
  idempotency_key varchar(160) not null,
  drawn_at timestamp with time zone not null,
  constraint uk_lottery_draw_activity_idempotency unique (activity_id, idempotency_key)
);

create table screen_templates (
  id uuid not null primary key,
  activity_id uuid not null,
  name varchar(120) not null,
  description varchar(500),
  preset boolean not null,
  components_json text not null,
  created_at timestamp with time zone not null,
  updated_at timestamp with time zone not null
);
create index idx_screen_templates_activity on screen_templates(activity_id);

create table screen_devices (
  id uuid not null primary key,
  activity_id uuid not null,
  name varchar(120) not null,
  device_token_hash varchar(64) not null unique,
  viewport_width integer,
  viewport_height integer,
  current_template_id uuid,
  display_mode varchar(32) not null,
  display_payload_json text not null,
  font_scale integer not null,
  volume integer not null,
  scroll_position integer not null,
  auto_scroll boolean not null,
  status varchar(16) not null,
  last_seen_at timestamp with time zone not null,
  created_at timestamp with time zone not null,
  updated_at timestamp with time zone not null
);
create index idx_screen_devices_activity on screen_devices(activity_id);

create table screen_activity_states (
  activity_id uuid not null primary key,
  presets_initialized boolean not null
);
