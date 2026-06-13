-- MySQL 初始化脚本。
-- 主业务库统一使用 MySQL，便于贴近国内 Java 后端项目栈。

set names utf8mb4;

create table if not exists sys_user (
  id bigint primary key auto_increment comment '用户主键',
  tenant_id bigint not null comment '租户 ID，用于多租户数据隔离',
  username varchar(128) not null comment '登录用户名或企业账号',
  display_name varchar(128) not null comment '页面展示名称',
  dept_id bigint comment '部门 ID，后续用于知识库 ACL 权限过滤',
  role_code varchar(64) not null default 'USER' comment '角色编码，例如 ADMIN、USER',
  created_at timestamp not null default current_timestamp comment '创建时间',
  unique key uk_user_tenant_username (tenant_id, username)
) engine=InnoDB default charset=utf8mb4 comment='系统用户表，演示租户、用户和权限上下文';

create table if not exists kb_space (
  id bigint primary key auto_increment comment '知识库主键',
  tenant_id bigint not null comment '租户 ID',
  name varchar(255) not null comment '知识库名称',
  description text comment '知识库描述',
  visibility varchar(32) not null default 'PRIVATE' comment '可见性：PRIVATE、TEAM、PUBLIC',
  created_at timestamp not null default current_timestamp comment '创建时间'
) engine=InnoDB default charset=utf8mb4 comment='知识库空间表，一个租户可拥有多个知识库';

create table if not exists kb_document (
  id bigint primary key auto_increment comment '文档主键',
  tenant_id bigint not null comment '租户 ID',
  kb_id bigint not null comment '所属知识库 ID',
  title varchar(255) not null comment '文档标题',
  source_type varchar(32) not null comment '来源类型：MARKDOWN、PDF、URL、DOCX 等',
  source_uri text comment '来源地址或本地路径',
  acl_policy json comment '文档级权限策略，例如允许的部门、角色、用户',
  version int not null default 1 comment '文档版本号，用于重建索引和回滚',
  status varchar(32) not null default 'PENDING' comment '处理状态：PENDING、INDEXED、FAILED',
  content_hash varchar(64) comment '内容哈希，用于判断是否重复导入',
  created_at timestamp not null default current_timestamp comment '创建时间',
  updated_at timestamp not null default current_timestamp on update current_timestamp comment '更新时间',
  key idx_doc_kb (kb_id),
  constraint fk_doc_kb foreign key (kb_id) references kb_space(id)
) engine=InnoDB default charset=utf8mb4 comment='知识库文档元数据表，不直接承载向量';

create table if not exists kb_doc_chunk (
  id bigint primary key auto_increment comment '文档分片主键',
  tenant_id bigint not null comment '租户 ID',
  doc_id bigint not null comment '所属文档 ID',
  chunk_no int not null comment '文档内分片序号',
  title_path text comment '标题层级路径，例如 支付/回调/错误码',
  content text not null comment '分片正文内容',
  token_count int not null comment '分片 token 估算数量',
  vector_id varchar(128) not null comment '向量库中的 point id，关联 Qdrant',
  metadata json comment '分片元数据，例如页码、段落、实体、ACL 快照',
  created_at timestamp not null default current_timestamp comment '创建时间',
  key idx_chunk_doc (doc_id),
  key idx_chunk_vector (vector_id),
  fulltext key ft_chunk_content (title_path, content),
  constraint fk_chunk_doc foreign key (doc_id) references kb_document(id)
) engine=InnoDB default charset=utf8mb4 comment='文档分片表，MySQL 负责元数据和关键词兜底检索';

create table if not exists kg_entity (
  id bigint primary key auto_increment comment '实体主键',
  tenant_id bigint not null comment '租户 ID',
  name varchar(255) not null comment '实体名称，例如 PAY_5001、支付回调、/api/orders',
  entity_type varchar(64) not null comment '实体类型：ErrorCode、ApiPath、Concept、Service 等',
  aliases json comment '实体别名数组，用于召回同义词',
  created_at timestamp not null default current_timestamp comment '创建时间',
  key idx_entity_name (tenant_id, entity_type, name)
) engine=InnoDB default charset=utf8mb4 comment='知识图谱实体镜像表，方便 MySQL 查询和管理';

create table if not exists kg_relation (
  id bigint primary key auto_increment comment '关系主键',
  tenant_id bigint not null comment '租户 ID',
  source_entity_id bigint not null comment '源实体 ID',
  target_entity_id bigint not null comment '目标实体 ID',
  relation_type varchar(64) not null comment '关系类型：MENTIONS、CAUSES、BELONGS_TO、RELATED_TO 等',
  evidence_chunk_id bigint comment '支撑该关系的文档分片 ID',
  created_at timestamp not null default current_timestamp comment '创建时间',
  key idx_relation_source (source_entity_id),
  key idx_relation_target (target_entity_id),
  constraint fk_relation_source foreign key (source_entity_id) references kg_entity(id),
  constraint fk_relation_target foreign key (target_entity_id) references kg_entity(id),
  constraint fk_relation_chunk foreign key (evidence_chunk_id) references kb_doc_chunk(id)
) engine=InnoDB default charset=utf8mb4 comment='知识图谱关系镜像表，Neo4j 是在线图谱检索主存储';

create table if not exists chat_session (
  id bigint primary key auto_increment comment '会话主键',
  tenant_id bigint not null comment '租户 ID',
  user_id bigint not null comment '发起会话的用户 ID',
  kb_id bigint comment '当前会话限定的知识库 ID，为空表示租户全局问答',
  title varchar(255) not null default 'New Session' comment '会话标题，默认从首条问题截断生成',
  created_at timestamp not null default current_timestamp comment '创建时间',
  updated_at timestamp not null default current_timestamp on update current_timestamp comment '最后更新时间',
  key idx_session_user (user_id, updated_at),
  key idx_session_kb (tenant_id, kb_id, updated_at)
) engine=InnoDB default charset=utf8mb4 comment='聊天会话表，用于沉淀多轮问答上下文';

create table if not exists chat_message (
  id bigint primary key auto_increment comment '消息主键',
  tenant_id bigint not null comment '租户 ID',
  session_id bigint not null comment '所属会话 ID',
  role varchar(32) not null comment '消息角色：user、assistant、system',
  content text not null comment '消息内容',
  trace_id varchar(128) comment 'Agent 执行链路 ID，关联 agent_trace',
  created_at timestamp not null default current_timestamp comment '创建时间',
  key idx_message_session (session_id, created_at),
  constraint fk_message_session foreign key (session_id) references chat_session(id)
) engine=InnoDB default charset=utf8mb4 comment='聊天消息表，保存用户问题和 AI 答案';

create table if not exists agent_trace (
  id bigint primary key auto_increment comment 'Trace 记录主键',
  trace_id varchar(128) not null comment '一次 Agent 执行的链路 ID',
  tenant_id bigint not null comment '租户 ID',
  message_id bigint comment '关联的聊天消息 ID',
  node_name varchar(128) not null comment 'Agent 节点名称，例如 RouterAgent、RetrieverAgent',
  input_summary text comment '节点输入摘要，避免记录过长原文',
  output_summary text comment '节点输出摘要',
  duration_ms int comment '节点耗时，单位毫秒',
  metadata json comment '节点扩展元数据，例如 filters、top_ids、风险标签',
  created_at timestamp not null default current_timestamp comment '创建时间',
  key idx_trace_id (trace_id)
) engine=InnoDB default charset=utf8mb4 comment='Agent 执行轨迹表，用于排查、演示和评测';

create table if not exists incident_diagnosis_history (
  id bigint primary key auto_increment comment '故障诊断历史主键',
  tenant_id bigint not null comment '租户 ID',
  user_id bigint not null comment '发起诊断的用户 ID',
  service_name varchar(128) not null comment '故障所属服务名',
  business_trace_id varchar(128) comment '业务系统日志 traceId',
  agent_trace_id varchar(128) comment '诊断 Agent 返回的 traceId',
  question text not null comment '用户输入的故障描述',
  summary text comment '诊断摘要',
  response_json json comment '完整诊断响应 JSON',
  created_at timestamp not null default current_timestamp comment '创建时间',
  key idx_incident_history_user (tenant_id, user_id, created_at),
  key idx_incident_history_trace (business_trace_id)
) engine=InnoDB default charset=utf8mb4 comment='故障诊断历史表，用于回看研发排障结果';

create table if not exists eval_case (
  id bigint primary key auto_increment comment '评测用例主键',
  project_code varchar(32) not null comment '项目编码，例如 RAG、INCIDENT',
  case_type varchar(64) not null comment '用例类型，例如 fact、procedure、diagnosis',
  question text not null comment '测试问题',
  expected_answer text comment '期望答案要点',
  expected_refs json comment '期望引用的文档或 chunk',
  dataset_version varchar(64) not null default 'v1' comment '评测集版本',
  created_at timestamp not null default current_timestamp comment '创建时间'
) engine=InnoDB default charset=utf8mb4 comment='离线评测用例表';

create table if not exists eval_result (
  id bigint primary key auto_increment comment '评测结果主键',
  run_id varchar(128) not null comment '一次评测运行 ID',
  case_id bigint not null comment '关联评测用例 ID',
  answer text comment 'Agent 实际答案',
  metrics json comment '评测指标，例如 Recall@5、faithfulness、latency',
  created_at timestamp not null default current_timestamp comment '创建时间',
  key idx_eval_run (run_id),
  constraint fk_eval_case foreign key (case_id) references eval_case(id)
) engine=InnoDB default charset=utf8mb4 comment='离线评测结果表';

create table if not exists svc_service (
  id bigint primary key auto_increment comment '服务主键',
  name varchar(128) not null comment '服务名称，例如 order、payment',
  env varchar(32) not null default 'demo' comment '环境标识：demo、test、prod',
  owner varchar(128) comment '服务负责人',
  repo_url text comment '代码仓库地址',
  created_at timestamp not null default current_timestamp comment '创建时间',
  key idx_service_name (name)
) engine=InnoDB default charset=utf8mb4 comment='微服务基础信息表';

create table if not exists obs_log_event (
  id bigint primary key auto_increment comment '日志事件主键',
  service_id bigint comment '所属服务 ID',
  trace_id varchar(128) comment '链路追踪 ID',
  level varchar(16) comment '日志级别：INFO、WARN、ERROR',
  endpoint varchar(255) comment '请求接口路径',
  exception_type varchar(255) comment '异常类型，例如 NullPointerException',
  message text comment '日志消息',
  stacktrace text comment '异常堆栈',
  occurred_at timestamp not null comment '日志发生时间',
  metadata json comment '扩展字段，例如 pod、host、requestId',
  key idx_log_trace (trace_id),
  key idx_log_service_time (service_id, occurred_at),
  key idx_log_exception (exception_type),
  constraint fk_log_service foreign key (service_id) references svc_service(id)
) engine=InnoDB default charset=utf8mb4 comment='可观测日志事件表，供故障诊断 Agent 检索';

create table if not exists code_symbol (
  id bigint primary key auto_increment comment '代码片段主键',
  service_name varchar(128) not null comment '服务名称',
  file_path text not null comment '源码文件路径',
  symbol_name varchar(255) comment '类名、方法名或函数名',
  language varchar(32) not null default 'java' comment '编程语言',
  content text not null comment '代码片段内容',
  vector_id varchar(128) comment '代码向量库 point id，后续可关联 Qdrant',
  metadata json comment '扩展元数据，例如行号、包名、调用关系',
  created_at timestamp not null default current_timestamp comment '创建时间',
  key idx_code_service (service_name),
  fulltext key ft_code_content (file_path, symbol_name, content)
) engine=InnoDB default charset=utf8mb4 comment='代码索引表，用于自然语言检索相关 Java 代码';

create table if not exists incident_ticket (
  id bigint primary key auto_increment comment '历史工单主键',
  service_name varchar(128) not null comment '服务名称',
  symptom text not null comment '故障现象',
  root_cause text not null comment '历史根因',
  solution text not null comment '历史解决方案',
  severity varchar(32) not null default 'P2' comment '故障等级：P0、P1、P2、P3',
  vector_id varchar(128) comment '工单向量库 point id，后续可关联 Qdrant',
  created_at timestamp not null default current_timestamp comment '创建时间',
  fulltext key ft_ticket_content (symptom, root_cause, solution)
) engine=InnoDB default charset=utf8mb4 comment='历史故障工单表，用于相似案例召回';

insert ignore into sys_user (tenant_id, username, display_name, dept_id, role_code)
values (1, 'demo', 'Demo User', 1, 'ADMIN');

insert ignore into kb_space (id, tenant_id, name, description, visibility)
values (1, 1, 'Demo Knowledge Base', '面试演示用知识库', 'PRIVATE');
