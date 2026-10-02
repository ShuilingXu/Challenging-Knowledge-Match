update answer_submissions set awarded_points=0 where status='PENDING_REVIEW';
insert into score_ledgers(id,activity_id,participant_id,question_id,submission_id,points,entry_type,note,created_at)
select s.id,s.activity_id,s.participant_id,s.question_id,s.id,
 s.awarded_points - coalesce((select sum(l.points) from score_ledgers l where l.submission_id=s.id),0),
 'REVIEW_CORRECTION','Reconcile reviewed submission with score ledger',current_timestamp
from answer_submissions s
where s.awarded_points <> coalesce((select sum(l.points) from score_ledgers l where l.submission_id=s.id),0);
update participants set score=score+coalesce((select sum(l.points) from score_ledgers l where l.participant_id=participants.id and l.entry_type='REVIEW_CORRECTION'),0)
where exists(select 1 from score_ledgers l where l.participant_id=participants.id and l.entry_type='REVIEW_CORRECTION');
update answer_submissions set status=case
 when awarded_points >= (select q.full_score from questions q where q.id=answer_submissions.question_id) then 'CORRECT'
 when awarded_points > 0 then 'PARTIAL' else 'INCORRECT' end
where status='SCORED';
