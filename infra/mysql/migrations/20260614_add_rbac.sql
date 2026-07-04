-- 多租户 RBAC 权限体系迁移（幂等）
-- 前置条件：20260614_add_user_auth.sql 已执行
-- 兼容策略：若 sys_customer 已存在（init.sql 新架构），跳过 sys_tenant 创建

-- 1. 创建租户表（仅当 sys_customer 尚不存在时；新 init.sql 已直接创建 sys_customer）
create table if not exists sys_tenant (
  id bigint primary key auto_increment comment '租户主键',
  name varchar(128) not null comment '租户名称',
  contact_name varchar(128) comment '联系人',
  contact_email varchar(256) comment '联系邮箱',
  status varchar(32) not null default 'ACTIVE' comment 'ACTIVE / DISABLED',
  created_at timestamp not null default current_timestamp comment '创建时间'
) engine=InnoDB default charset=utf8mb4 comment='租户表，用于多租户数据隔离';

-- 2. 插入默认租户/客户（幂等：优先更新 sys_customer，若不存在则插 sys_tenant）
set @customer_exists = (select count(*) from information_schema.tables
    where table_schema = database() and table_name = 'sys_customer');
set @tenant_exists = (select count(*) from information_schema.tables
    where table_schema = database() and table_name = 'sys_tenant');

-- 若 sys_customer 存在 → 直接 upsert；否则 upsert sys_tenant
set @sql_upsert = if(@customer_exists > 0,
    'insert into sys_customer (id, name, status) values (1, ''默认客户'', ''ACTIVE'') on duplicate key update name = values(name), status = values(status)',
    'insert into sys_tenant (id, name, status) values (1, ''默认租户'', ''ACTIVE'') on duplicate key update name = values(name), status = values(status)');
prepare stmt from @sql_upsert;
execute stmt;
deallocate prepare stmt;

-- 3. 将 demo 用户升级为超级管理员（幂等）
update sys_user set role_code = 'SUPER_ADMIN' where username = 'demo';
