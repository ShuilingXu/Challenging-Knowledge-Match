alter table site_settings add column if not exists storage_enabled boolean;
alter table site_settings add column if not exists storage_region varchar(80);
alter table site_settings add column if not exists storage_access_key varchar(512);
alter table site_settings add column if not exists storage_secret_key varchar(2048);
alter table site_settings add column if not exists storage_session_token varchar(4096);
alter table site_settings add column if not exists storage_public_base_url varchar(1024);
