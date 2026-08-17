alter table questions add column if not exists text_accepted_answers text default '[]' not null;
alter table questions add column if not exists text_match_mode varchar(24) default 'MANUAL' not null;
