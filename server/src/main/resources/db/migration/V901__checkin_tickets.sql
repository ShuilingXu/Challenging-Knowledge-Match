alter table activities add column registration_allowed boolean not null default true;
create unique index uk_participant_activity_contact on participants(activity_id, contact);
create table checkin_tickets (
 id uuid primary key, activity_id uuid not null references activities(id),
 participant_id uuid not null references participants(id), token varchar(64) not null unique,
 ticket_type varchar(16) not null, note varchar(1000) not null,
 checkin_count integer not null default 0, last_checkin_at timestamp with time zone,
 created_at timestamp with time zone not null,
 constraint uk_ticket_activity_participant unique(activity_id, participant_id)
);
create table ticket_redemptions (
 id uuid primary key, ticket_id uuid not null references checkin_tickets(id),
 request_id uuid not null, staff_id uuid not null, redeemed_at timestamp with time zone not null,
 constraint uk_ticket_redemption_request unique(ticket_id, request_id)
);
