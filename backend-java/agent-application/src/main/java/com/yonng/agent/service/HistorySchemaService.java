package com.yonng.agent.service;

import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 历史功能的轻量 schema 兜底。
 *
 * <p>项目没有引入 Flyway/Liquibase，旧本地库可能缺少新字段。
 * 这里启动时补齐演示必需的历史表结构，避免页面历史接口直接 500。</p>
 */
@Service
public class HistorySchemaService {

    private final DataSource dataSource;

    public HistorySchemaService(DataSource dataSource) {
        this.dataSource = dataSource;
        ensureSchema();
    }

    public void ensureSchema() {
        try (Connection conn = dataSource.getConnection()) {
            ensureChatSessionKbId(conn);
            ensureIncidentHistory(conn);
        } catch (SQLException ignored) {
            // schema 兜底不能影响主服务启动；接口层仍会做降级保护。
        }
    }

    private void ensureChatSessionKbId(Connection conn) throws SQLException {
        if (!columnExists(conn, "chat_session", "kb_id")) {
            executeSilently(conn, "alter table chat_session add column kb_id bigint null comment '当前会话限定的知识库 ID，为空表示租户全局问答' after user_id");
        }
        if (!indexExists(conn, "chat_session", "idx_session_kb")) {
            executeSilently(conn, "alter table chat_session add key idx_session_kb (tenant_id, kb_id, updated_at)");
        }
    }

    private void ensureIncidentHistory(Connection conn) throws SQLException {
        executeSilently(conn, """
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
                ) engine=InnoDB default charset=utf8mb4 comment='故障诊断历史表，用于回看研发排障结果'
                """);
    }

    private boolean columnExists(Connection conn, String table, String column) throws SQLException {
        try (ResultSet rs = conn.getMetaData().getColumns(conn.getCatalog(), null, table, column)) {
            return rs.next();
        }
    }

    private boolean indexExists(Connection conn, String table, String index) throws SQLException {
        try (ResultSet rs = conn.getMetaData().getIndexInfo(conn.getCatalog(), null, table, false, false)) {
            while (rs.next()) {
                if (index.equals(rs.getString("INDEX_NAME"))) {
                    return true;
                }
            }
            return false;
        }
    }

    private void executeSilently(Connection conn, String sql) {
        try (Statement statement = conn.createStatement()) {
            statement.execute(sql);
        } catch (SQLException ignored) {
            // 并发启动或权限不足时交给调用方降级。
        }
    }
}
