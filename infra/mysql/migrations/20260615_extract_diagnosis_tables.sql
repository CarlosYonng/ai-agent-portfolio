-- 故障诊断已剥离到 diagnosis_db；Agent Ops 主库只保留 RAG、Trace、用户和知识库数据。
drop table if exists incident_diagnosis_history;
drop table if exists obs_log_event;
drop table if exists code_symbol;
drop table if exists incident_ticket;
drop table if exists svc_service;
