alter table question_sets add column source_question_set_id uuid;
create unique index uk_question_set_activity_source on question_sets(activity_id, source_question_set_id);
