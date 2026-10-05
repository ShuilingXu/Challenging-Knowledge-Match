alter table questions add column source_question_id uuid;
create unique index uk_question_activity_source on questions(activity_id, source_question_id);
