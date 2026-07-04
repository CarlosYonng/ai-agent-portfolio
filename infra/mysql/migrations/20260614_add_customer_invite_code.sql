-- 为客户注册链路添加邀请码。
-- 设计约束：公开注册只能通过客户邀请码创建 USER；平台账号由 SUPER_ADMIN 后台创建。

alter table sys_customer
    add column invite_code varchar(64) null comment '客户注册邀请码' after contact_email;

update sys_customer
set invite_code = concat('CUST-', upper(substr(replace(uuid(), '-', ''), 1, 8)))
where invite_code is null or invite_code = '';

alter table sys_customer
    modify invite_code varchar(64) not null comment '客户注册邀请码';

alter table sys_customer
    add unique key uk_customer_invite_code (invite_code);

alter table sys_user
    modify customer_id bigint null comment '所属客户 ID；平台账号为空，客户用户必填';

alter table sys_user
    add unique key uk_user_username (username);
