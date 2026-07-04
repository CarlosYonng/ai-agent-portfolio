-- 为 chat_message 表添加 citations 字段，保存引用证据 JSON（幂等迁移）
set @col_exists = (select count(*) from information_schema.columns
    where table_schema = database() and table_name = 'chat_message' and column_name = 'citations');
set @sql_add_citations = if(@col_exists = 0,
    'alter table chat_message add column citations json null comment ''关联回答的引用证据 JSON'' after trace_id',
    'select ''column citations already exists'' as msg');
prepare stmt from @sql_add_citations;
execute stmt;
deallocate prepare stmt;
