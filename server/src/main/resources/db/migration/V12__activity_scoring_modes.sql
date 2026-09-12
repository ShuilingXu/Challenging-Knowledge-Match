alter table activities add column if not exists scoring_mode varchar(16) not null default 'SIMPLE';
alter table activities add column if not exists correct_score_percent integer not null default 100;
alter table activities add column if not exists incorrect_score_percent integer not null default 0;
alter table activities add column if not exists scoring_rules text not null default '{}';
