-- 1. 插入平台客户（幂等：已存在时更新 name/status）
insert into sys_customer (id, name, invite_code, status)
values (2, 'Agent Ops 平台', 'PLATFORM', 'ACTIVE')
on duplicate key update
  name = values(name),
  invite_code = values(invite_code),
  status = values(status);

-- 2. 将平台管理员用户关联到平台客户
set @platform_id = (select id from sys_customer where invite_code = 'PLATFORM');
update sys_user set customer_id = @platform_id where role_code in ('SUPER_ADMIN', 'PLATFORM_ADMIN');
