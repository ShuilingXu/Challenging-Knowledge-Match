-- Performance indexes for the 50-concurrency target.
--
-- V1 created answer_submissions without any activity-scoped index: a submission lookup and the
-- twice-per-answer submission count could only fall back on uk(activity_id, idempotency_key) and
-- then scan every submission in the activity. That cost grows with each event because the database
-- keeps every historical activity, not just the running one.
--
-- buzz_entries needs nothing here: uq_buzz_participant already indexes
-- (activity_id, question_id, participant_id), whose leftmost prefix serves the question lookups.

create index if not exists idx_answer_submissions_activity_question
  on answer_submissions (activity_id, question_id);

create index if not exists idx_answer_submissions_activity_participant
  on answer_submissions (activity_id, participant_id);

create index if not exists idx_questions_activity_display_order
  on questions (activity_id, display_order);

create index if not exists idx_prize_awards_activity_participant
  on prize_awards (activity_id, participant_id);

create index if not exists idx_lottery_draws_activity
  on lottery_draws (activity_id);
