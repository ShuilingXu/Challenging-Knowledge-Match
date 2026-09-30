-- ADVANCED mode was retired; its rank based behavior is provided by GENERAL.
-- Preserve all configured percentages and rank rules while normalizing legacy rows.
update activities
set scoring_mode = 'GENERAL'
where upper(scoring_mode) = 'ADVANCED';
