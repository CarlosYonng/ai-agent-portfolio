set names utf8mb4;

alter table chat_session
  add column kb_id bigint null comment '当前会话限定的知识库 ID，为空表示租户全局问答' after user_id,
  add key idx_session_kb (tenant_id, kb_id, updated_at);
