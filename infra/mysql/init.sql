-- MySQL 初始化脚本。
-- 主业务库统一使用 MySQL，便于贴近国内 Java 后端项目栈。

set names utf8mb4;

create table if not exists sys_customer (
  id bigint primary key auto_increment comment '客户主键',
  name varchar(128) not null comment '客户名称',
  contact_name varchar(128) comment '联系人',
  contact_email varchar(256) comment '联系邮箱',
  invite_code varchar(64) not null comment '客户注册邀请码',
  status varchar(32) not null default 'ACTIVE' comment '状态',
  created_at timestamp not null default current_timestamp comment '创建时间',
  unique key uk_customer_name (name),
  unique key uk_customer_invite_code (invite_code)
) engine=InnoDB default charset=utf8mb4 comment='客户表，用于多客户数据隔离和公开注册邀请码';

create table if not exists sys_user (
  id bigint primary key auto_increment comment '用户主键',
  customer_id bigint comment '所属客户 ID；平台管理员也分配 PLATFORM 客户 ID，数据隔离通过 RoleAccess 按角色控制',
  username varchar(128) not null comment '登录用户名或企业账号',
  display_name varchar(128) not null comment '页面展示名称',
  dept_id bigint comment '部门 ID，后续用于知识库 ACL 权限过滤',
  role_code varchar(64) not null default 'USER' comment '角色编码，例如 ADMIN、USER',
  password_hash varchar(256) not null comment 'BCrypt 密码哈希值',
  email varchar(256) comment '邮箱',
  status varchar(32) not null default 'ACTIVE' comment '状态：ACTIVE、DISABLED',
  last_login_at timestamp null comment '最后登录时间',
  created_at timestamp not null default current_timestamp comment '创建时间',
  unique key uk_user_username (username),
  key idx_user_customer (customer_id)
) engine=InnoDB default charset=utf8mb4 comment='系统用户表，演示客户、用户和权限上下文';

create table if not exists kb_space (
  id bigint primary key auto_increment comment '知识库主键',
  customer_id bigint not null comment '客户 ID',
  name varchar(255) not null comment '知识库名称',
  description text comment '知识库描述',
  visibility varchar(32) not null default 'PRIVATE' comment '可见性：PRIVATE、TEAM、PUBLIC',
  created_at timestamp not null default current_timestamp comment '创建时间'
) engine=InnoDB default charset=utf8mb4 comment='知识库空间表，一个客户可拥有多个知识库';

create table if not exists kb_document (
  id bigint primary key auto_increment comment '文档主键',
  customer_id bigint not null comment '客户 ID',
  kb_id bigint not null comment '所属知识库 ID',
  title varchar(255) not null comment '文档标题',
  source_type varchar(32) not null comment '来源类型：MARKDOWN、PDF、URL、DOCX 等',
  source_uri text comment '来源地址或本地路径',
  acl_policy json comment '文档级权限策略，例如允许的部门、角色、用户',
  version int not null default 1 comment '文档版本号，用于重建索引和回滚',
  status varchar(32) not null default 'PENDING' comment '处理状态：PENDING、INDEXED、FAILED',
  ingest_stage varchar(64) not null default 'REGISTERED' comment '入库阶段：REGISTERED、UPLOADED、CHUNKING、EMBEDDING、WRITING_VECTOR、INDEXED、FAILED',
  progress_percent int not null default 0 comment '入库进度百分比，供管理台展示',
  chunk_total int null comment '文档切片总数',
  chunk_done int not null default 0 comment '已处理切片数',
  error_message text null comment '最近一次入库失败原因',
  indexed_at timestamp null comment '最近一次成功索引时间',
  content_hash varchar(64) comment '内容哈希，用于判断是否重复导入',
  created_at timestamp not null default current_timestamp comment '创建时间',
  updated_at timestamp not null default current_timestamp on update current_timestamp comment '更新时间',
  key idx_doc_kb (kb_id),
  constraint fk_doc_kb foreign key (kb_id) references kb_space(id)
) engine=InnoDB default charset=utf8mb4 comment='知识库文档元数据表，不直接承载向量';

create table if not exists chat_session (
  id bigint primary key auto_increment comment '会话主键',
  customer_id bigint not null comment '客户 ID',
  user_id bigint not null comment '发起会话的用户 ID',
  kb_id bigint comment '当前会话限定的知识库 ID，为空表示客户全局问答',
  title varchar(255) not null default 'New Session' comment '会话标题，默认从首条问题截断生成',
  created_at timestamp not null default current_timestamp comment '创建时间',
  updated_at timestamp not null default current_timestamp on update current_timestamp comment '最后更新时间',
  key idx_session_user (user_id, updated_at),
  key idx_session_kb (customer_id, kb_id, updated_at)
) engine=InnoDB default charset=utf8mb4 comment='聊天会话表，用于沉淀多轮问答上下文';

create table if not exists chat_message (
  id bigint primary key auto_increment comment '消息主键',
  customer_id bigint not null comment '客户 ID',
  session_id bigint not null comment '所属会话 ID',
  role varchar(32) not null comment '消息角色：user、assistant、system',
  content text not null comment '消息内容',
  trace_id varchar(128) comment 'Agent 执行链路 ID，关联 agent_trace',
  citations json null comment '关联回答的引用证据 JSON，含 chunk/doc/title/score/text',
  created_at timestamp not null default current_timestamp comment '创建时间',
  key idx_message_session (session_id, created_at),
  constraint fk_message_session foreign key (session_id) references chat_session(id)
) engine=InnoDB default charset=utf8mb4 comment='聊天消息表，保存用户问题和 AI 答案';

create table if not exists agent_trace (
  id bigint primary key auto_increment comment 'Trace 记录主键',
  trace_id varchar(128) not null comment '一次 Agent 执行的链路 ID',
  customer_id bigint not null comment '客户 ID',
  message_id bigint comment '关联的聊天消息 ID',
  node_name varchar(128) not null comment 'Agent 节点名称，例如 RouterAgent、RetrieverAgent',
  input_summary text comment '节点输入摘要，避免记录过长原文',
  output_summary text comment '节点输出摘要',
  duration_ms int comment '节点耗时，单位毫秒',
  metadata json comment '节点扩展元数据，例如 filters、top_ids、风险标签',
  created_at timestamp not null default current_timestamp comment '创建时间',
  key idx_trace_id (trace_id)
) engine=InnoDB default charset=utf8mb4 comment='Agent 执行轨迹表，用于排查和演示';

insert into sys_customer (id, name, invite_code, status) values
  (1, '默认客户', 'CUST-DEMO', 'ACTIVE'),
  (2, 'Agent Ops 平台', 'PLATFORM', 'ACTIVE')
on duplicate key update
  name = values(name),
  invite_code = values(invite_code),
  status = values(status);

insert into sys_user (id, customer_id, username, display_name, dept_id, role_code, password_hash, email, status)
values (1, 2, 'demo', 'Demo User', 1, 'SUPER_ADMIN', '$2y$10$Xn8XlLEFj801URW/97dFWui4EhfBHY8XiQ9FHpZJbznA05VbdON7e', 'demo@example.com', 'ACTIVE')
on duplicate key update
  customer_id = values(customer_id),
  display_name = values(display_name),
  role_code = values(role_code),
  password_hash = values(password_hash),
  email = values(email),
  status = values(status);

insert ignore into kb_space (id, customer_id, name, description, visibility)
values (1, 1, 'Demo Knowledge Base', '面试演示用知识库', 'PRIVATE');
