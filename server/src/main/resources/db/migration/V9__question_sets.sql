alter table activities add column if not exists parent_activity_id uuid;
alter table activities add column if not exists activity_type varchar(24) default 'EVENT' not null;
alter table activities add column if not exists active_question_set_id uuid;

create table question_sets (
  id uuid not null primary key,
  activity_id uuid not null,
  name varchar(180) not null,
  description varchar(1000),
  active boolean not null default false,
  created_at timestamp with time zone not null,
  updated_at timestamp with time zone not null,
  version bigint default 0 not null,
  constraint uk_question_set_activity_name unique (activity_id, name)
);
create index idx_question_sets_activity_updated on question_sets(activity_id, updated_at);

create table question_set_items (
  id uuid not null primary key,
  question_set_id uuid not null,
  question_id uuid not null,
  display_order integer not null,
  version bigint default 0 not null,
  constraint uk_question_set_item_question unique (question_set_id, question_id),
  constraint uk_question_set_item_order unique (question_set_id, display_order)
);
create index idx_question_set_items_set_order on question_set_items(question_set_id, display_order);
