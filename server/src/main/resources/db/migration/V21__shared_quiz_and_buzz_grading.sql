alter table activities add column shared_participants boolean not null default false;
update activities set shared_participants = true where activity_type = 'LOTTERY';
-- Existing quiz identities remain local; empty quizzes can safely join the main roster.
update activities set shared_participants = true where activity_type = 'QUIZ'
  and not exists (select 1 from participants p where p.activity_id = activities.id)
  and not exists (select 1 from venues v where v.activity_id = activities.id);
alter table buzz_entries add column awarded_points integer;
alter table buzz_entries add column correct boolean;
alter table buzz_entries add column feedback varchar(1000);
