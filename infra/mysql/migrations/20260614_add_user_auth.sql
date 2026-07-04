-- 为用户体系添加认证所需字段（幂等迁移）
-- 前置条件：init.sql 或旧版 schema 已创建 sys_user 表
-- 注意：新版 init.sql 已包含这些字段，本迁移兼容「表已有列」的场景

-- 1. password_hash：BCrypt 哈希，NOT NULL
--    若列不存在则新增；若存在则跳过（MySQL 8.0 不支持 IF NOT EXISTS 于列级）
set @col_exists = (select count(*) from information_schema.columns
    where table_schema = database() and table_name = 'sys_user' and column_name = 'password_hash');
set @sql_add_hash = if(@col_exists = 0,
    'alter table sys_user add column password_hash varchar(256) not null comment ''BCrypt 密码哈希值'' after role_code',
    'select ''column password_hash already exists'' as msg');
prepare stmt from @sql_add_hash;
execute stmt;
deallocate prepare stmt;

-- 2. 为已有行填充默认密码哈希（demo123），避免 NULL 违规
update sys_user set password_hash = '$2y$10$Xn8XlLEFj801URW/97dFWui4EhfBHY8XiQ9FHpZJbznA05VbdON7e'
where password_hash = '' or password_hash is null;

-- 3. email
set @col_exists = (select count(*) from information_schema.columns
    where table_schema = database() and table_name = 'sys_user' and column_name = 'email');
set @sql_add_email = if(@col_exists = 0,
    'alter table sys_user add column email varchar(256) comment ''邮箱'' after password_hash',
    'select ''column email already exists'' as msg');
prepare stmt from @sql_add_email;
execute stmt;
deallocate prepare stmt;

-- 4. status
set @col_exists = (select count(*) from information_schema.columns
    where table_schema = database() and table_name = 'sys_user' and column_name = 'status');
set @sql_add_status = if(@col_exists = 0,
    'alter table sys_user add column status varchar(32) not null default ''ACTIVE'' comment ''状态：ACTIVE、DISABLED'' after email',
    'select ''column status already exists'' as msg');
prepare stmt from @sql_add_status;
execute stmt;
deallocate prepare stmt;

-- 5. last_login_at
set @col_exists = (select count(*) from information_schema.columns
    where table_schema = database() and table_name = 'sys_user' and column_name = 'last_login_at');
set @sql_add_login = if(@col_exists = 0,
    'alter table sys_user add column last_login_at timestamp null comment ''最后登录时间'' after status',
    'select ''column last_login_at already exists'' as msg');
prepare stmt from @sql_add_login;
execute stmt;
deallocate prepare stmt;

-- 6. 确保演示用户存在且密码正确（密码：demo123）
insert into sys_user (id, customer_id, username, display_name, dept_id, role_code, password_hash, email, status)
values (1, 2, 'demo', 'Demo User', 1, 'SUPER_ADMIN', '$2y$10$Xn8XlLEFj801URW/97dFWui4EhfBHY8XiQ9FHpZJbznA05VbdON7e', 'demo@example.com', 'ACTIVE')
on duplicate key update
    password_hash = values(password_hash),
    role_code = values(role_code),
    status = values(status);
