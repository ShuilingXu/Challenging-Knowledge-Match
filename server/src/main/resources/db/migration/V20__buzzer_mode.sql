alter table activities add column answer_mode varchar(16) not null default 'STANDARD';
create table buzz_entries (
  id uuid primary key,
  activity_id uuid not null references activities(id),
  question_id uuid not null references questions(id),
  participant_id uuid not null references participants(id),
  response_rank integer not null,
  buzzed_at timestamp with time zone not null,
  constraint uq_buzz_participant unique (activity_id, question_id, participant_id),
  constraint uq_buzz_rank unique (activity_id, question_id, response_rank)
);
