alter table activities add column if not exists client_display_name varchar(160);
alter table activities add column if not exists client_theme_color varchar(16);
alter table activities add column if not exists client_hero_image_url varchar(1024);
alter table activities add column if not exists client_background_image_url varchar(1024);

create table if not exists site_settings (
  id bigint not null primary key,
  domain varchar(255) not null,
  site_name varchar(160) not null,
  logo_url varchar(1024),
  footer_code text
);
