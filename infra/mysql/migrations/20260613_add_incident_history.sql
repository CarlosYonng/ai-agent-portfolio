set names utf8mb4;

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
