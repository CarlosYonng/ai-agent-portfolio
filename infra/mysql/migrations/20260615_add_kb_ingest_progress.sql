-- 为知识库文档补充入库进度和失败原因，便于管理台展示处理阶段和支持失败重试。
-- MySQL 8.0 列级 ADD COLUMN 不支持 IF NOT EXISTS，这里用 information_schema 做幂等判断。

set @col_exists = (select count(*) from information_schema.columns
    where table_schema = database() and table_name = 'kb_document' and column_name = 'ingest_stage');
set @sql_add_ingest_stage = if(@col_exists = 0,
    'alter table kb_document add column ingest_stage varchar(64) not null default ''REGISTERED'' comment ''入库阶段：REGISTERED、UPLOADED、CHUNKING、EMBEDDING、WRITING_VECTOR、INDEXED、FAILED'' after status',
    'select ''column ingest_stage already exists'' as msg');
prepare stmt from @sql_add_ingest_stage;
execute stmt;
deallocate prepare stmt;

set @col_exists = (select count(*) from information_schema.columns
    where table_schema = database() and table_name = 'kb_document' and column_name = 'progress_percent');
set @sql_add_progress_percent = if(@col_exists = 0,
    'alter table kb_document add column progress_percent int not null default 0 comment ''入库进度百分比，供管理台展示'' after ingest_stage',
    'select ''column progress_percent already exists'' as msg');
prepare stmt from @sql_add_progress_percent;
execute stmt;
deallocate prepare stmt;

set @col_exists = (select count(*) from information_schema.columns
    where table_schema = database() and table_name = 'kb_document' and column_name = 'chunk_total');
set @sql_add_chunk_total = if(@col_exists = 0,
    'alter table kb_document add column chunk_total int null comment ''文档切片总数'' after progress_percent',
    'select ''column chunk_total already exists'' as msg');
prepare stmt from @sql_add_chunk_total;
execute stmt;
deallocate prepare stmt;

set @col_exists = (select count(*) from information_schema.columns
    where table_schema = database() and table_name = 'kb_document' and column_name = 'chunk_done');
set @sql_add_chunk_done = if(@col_exists = 0,
    'alter table kb_document add column chunk_done int not null default 0 comment ''已处理切片数'' after chunk_total',
    'select ''column chunk_done already exists'' as msg');
prepare stmt from @sql_add_chunk_done;
execute stmt;
deallocate prepare stmt;

set @col_exists = (select count(*) from information_schema.columns
    where table_schema = database() and table_name = 'kb_document' and column_name = 'error_message');
set @sql_add_error_message = if(@col_exists = 0,
    'alter table kb_document add column error_message text null comment ''最近一次入库失败原因'' after chunk_done',
    'select ''column error_message already exists'' as msg');
prepare stmt from @sql_add_error_message;
execute stmt;
deallocate prepare stmt;

set @col_exists = (select count(*) from information_schema.columns
    where table_schema = database() and table_name = 'kb_document' and column_name = 'indexed_at');
set @sql_add_indexed_at = if(@col_exists = 0,
    'alter table kb_document add column indexed_at timestamp null comment ''最近一次成功索引时间'' after error_message',
    'select ''column indexed_at already exists'' as msg');
prepare stmt from @sql_add_indexed_at;
execute stmt;
deallocate prepare stmt;
