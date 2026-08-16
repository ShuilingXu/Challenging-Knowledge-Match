alter table site_settings add column if not exists storage_endpoint varchar(1024);
alter table site_settings add column if not exists storage_bucket varchar(160);
