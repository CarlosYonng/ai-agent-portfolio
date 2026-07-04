-- 将租户概念全面重命名为客户，统一业内命名规范
-- 前置条件：20260614_add_rbac.sql 已执行
-- 幂等设计：兼容 init.sql 已直接创建 sys_customer 的场景

-- 1. 重命名 sys_tenant → sys_customer（仅当 sys_customer 尚不存在时）
set @customer_exists = (select count(*) from information_schema.tables
    where table_schema = database() and table_name = 'sys_customer');
set @tenant_exists = (select count(*) from information_schema.tables
    where table_schema = database() and table_name = 'sys_tenant');

set @sql_rename = if(@customer_exists = 0 and @tenant_exists > 0,
    'rename table sys_tenant to sys_customer',
    'select ''skip rename: sys_customer already exists or sys_tenant not found'' as msg');
prepare stmt from @sql_rename;
execute stmt;
deallocate prepare stmt;

-- 如果 sys_tenant 还存在但未重命名（极端情况），补充重试
set @tenant_still_exists = (select count(*) from information_schema.tables
    where table_schema = database() and table_name = 'sys_tenant');
set @customer_still_missing = (select count(*) from information_schema.tables
    where table_schema = database() and table_name = 'sys_customer');
set @sql_rename2 = if(@tenant_still_exists > 0 and @customer_still_missing = 0,
    'rename table sys_tenant to sys_customer_backup',  -- 不覆盖现有表
    'select ''no rename needed'' as msg2');
-- 注意：此处仅作保护性提示，不会覆盖已有 sys_customer
prepare stmt from @sql_rename2;
execute stmt;
deallocate prepare stmt;

-- 2. 列注释更新（幂等：不影响已有列定义）
alter table sys_customer modify column name varchar(128) not null comment '客户名称';
alter table sys_customer modify column status varchar(32) not null default 'ACTIVE' comment '状态';
alter table sys_customer modify column contact_name varchar(128) comment '联系人';
alter table sys_customer modify column contact_email varchar(256) comment '联系邮箱';

-- 3. 所有表中 tenant_id → customer_id（幂等：仅重命名仍叫 tenant_id 的列）
set @col_tenant = (select count(*) from information_schema.columns
    where table_schema = database() and table_name = 'sys_user' and column_name = 'tenant_id');
set @sql_user = if(@col_tenant > 0,
    'alter table sys_user change tenant_id customer_id bigint not null comment ''所属客户 ID''',
    'select ''sys_user.customer_id already renamed'' as msg');
prepare stmt from @sql_user;
execute stmt;
deallocate prepare stmt;

set @col_tenant = (select count(*) from information_schema.columns
    where table_schema = database() and table_name = 'kb_space' and column_name = 'tenant_id');
set @sql_kb = if(@col_tenant > 0,
    'alter table kb_space change tenant_id customer_id bigint not null comment ''客户 ID''',
    'select ''kb_space.customer_id already renamed'' as msg');
prepare stmt from @sql_kb;
execute stmt;
deallocate prepare stmt;

set @col_tenant = (select count(*) from information_schema.columns
    where table_schema = database() and table_name = 'kb_document' and column_name = 'tenant_id');
set @sql_kbd = if(@col_tenant > 0,
    'alter table kb_document change tenant_id customer_id bigint not null comment ''客户 ID''',
    'select ''kb_document.customer_id already renamed'' as msg');
prepare stmt from @sql_kbd;
execute stmt;
deallocate prepare stmt;

set @col_tenant = (select count(*) from information_schema.columns
    where table_schema = database() and table_name = 'chat_session' and column_name = 'tenant_id');
set @sql_cs = if(@col_tenant > 0,
    'alter table chat_session change tenant_id customer_id bigint not null comment ''客户 ID''',
    'select ''chat_session.customer_id already renamed'' as msg');
prepare stmt from @sql_cs;
execute stmt;
deallocate prepare stmt;

set @col_tenant = (select count(*) from information_schema.columns
    where table_schema = database() and table_name = 'chat_message' and column_name = 'tenant_id');
set @sql_cm = if(@col_tenant > 0,
    'alter table chat_message change tenant_id customer_id bigint not null comment ''客户 ID''',
    'select ''chat_message.customer_id already renamed'' as msg');
prepare stmt from @sql_cm;
execute stmt;
deallocate prepare stmt;

set @col_tenant = (select count(*) from information_schema.columns
    where table_schema = database() and table_name = 'agent_trace' and column_name = 'tenant_id');
set @sql_at = if(@col_tenant > 0,
    'alter table agent_trace change tenant_id customer_id bigint not null comment ''客户 ID''',
    'select ''agent_trace.customer_id already renamed'' as msg');
prepare stmt from @sql_at;
execute stmt;
deallocate prepare stmt;

-- 4. 客户名称唯一约束（幂等：仅在不存在时添加）
set @uk_exists = (select count(*) from information_schema.statistics
    where table_schema = database() and table_name = 'sys_customer' and index_name = 'uk_customer_name');
set @sql_uk = if(@uk_exists = 0,
    'alter table sys_customer add unique key uk_customer_name (name)',
    'select ''uk_customer_name already exists'' as msg');
prepare stmt from @sql_uk;
execute stmt;
deallocate prepare stmt;
