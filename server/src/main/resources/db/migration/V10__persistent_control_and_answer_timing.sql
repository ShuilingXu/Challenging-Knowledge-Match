alter table activities add column control_stage varchar(32) not null default 'LOBBY';
alter table activities add column control_question_id uuid;
alter table activities add column control_seconds integer not null default 0;
alter table activities add column control_updated_at timestamp with time zone;
alter table activities add column question_opened_at timestamp with time zone;
alter table answer_submissions add column elapsed_seconds bigint;
alter table answer_submissions add column response_rank integer not null default 0;
