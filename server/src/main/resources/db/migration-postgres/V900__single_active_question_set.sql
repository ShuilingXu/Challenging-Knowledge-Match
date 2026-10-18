-- At most one active question set per activity.
--
-- Lives under db/migration-postgres because H2 (the test database) has no partial indexes, so this
-- cannot ship in the portable migration chain. The compose profile adds this directory to
-- spring.flyway.locations; tests only scan classpath:db/migration.
--
-- The application already deactivates the previous set in activateQuestionSet(), but nothing at the
-- database level enforced the rule. A concurrent activation or a direct edit could leave two active
-- sets on one activity, which makes findByActivityIdAndActiveTrue() throw NonUniqueResultException
-- and turns ordinary question reads into 500s.
create unique index if not exists uk_question_sets_single_active
  on question_sets (activity_id) where active;
