-- 为历史平台用户补全 PLATFORM 客户 ID。
--
-- 重构前平台管理员（SUPER_ADMIN / PLATFORM_ADMIN）的 customer_id 有意设为 null，
-- 作为"跨租户可见"的信号。重构后所有用户都有非空 customer_id，数据范围通过
-- RoleAccess 按角色控制，不再以 customer_id 为空判断权限。
--
-- 同时更新列注释，反映新的设计约定。

update sys_user su
  join sys_customer sc on sc.invite_code = 'PLATFORM'
  set su.customer_id = sc.id
where su.customer_id is null
  and su.role_code in ('SUPER_ADMIN', 'PLATFORM_ADMIN');

alter table sys_user
  modify column customer_id bigint null comment '所属客户 ID';
