package com.yonng.agent.service.system;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * 只补 active 表的兼容列，不处理已废弃的 kg_entity/kg_relation。</p>
 */
@Service
public class HistorySchemaService {

    private static final Logger log = LoggerFactory.getLogger(HistorySchemaService.class);

    private final DataSource dataSource;

    public HistorySchemaService(DataSource dataSource) {
        this.dataSource = dataSource;
        ensureSchema();
    }

    public void ensureSchema() {
        try (Connection conn = dataSource.getConnection()) {
            ensureCustomerColumnNames(conn);
            ensureChatSessionKbId(conn);
            ensureChatMessageCitations(conn);
        } catch (SQLException e) {
            log.warn("schema兜底失败，不影响主服务启动：{}", e.getMessage());
        }
    }

    private void ensureCustomerColumnNames(Connection conn) throws SQLException {
        renameTenantColumnIfNeeded(conn, "sys_user", "所属客户 ID");
        renameTenantColumnIfNeeded(conn, "kb_space", "客户 ID");
        renameTenantColumnIfNeeded(conn, "kb_document", "客户 ID");
        renameTenantColumnIfNeeded(conn, "chat_session", "客户 ID");
        renameTenantColumnIfNeeded(conn, "chat_message", "客户 ID");
        renameTenantColumnIfNeeded(conn, "agent_trace", "客户 ID");
    }

    private void renameTenantColumnIfNeeded(Connection conn, String table, String comment) throws SQLException {
        if (tableExists(conn, table)
                && columnExists(conn, table, "tenant_id")
                && !columnExists(conn, table, "customer_id")) {
            executeSilently(conn, "alter table " + table + " change tenant_id customer_id bigint not null comment '" + comment + "'");
        }
    }

    private void ensureChatSessionKbId(Connection conn) throws SQLException {
        if (!columnExists(conn, "chat_session", "kb_id")) {
            executeSilently(conn, "alter table chat_session add column kb_id bigint null comment '当前会话限定的知识库 ID，为空表示客户全局问答' after user_id");
        }
        if (!indexExists(conn, "chat_session", "idx_session_kb")) {
            executeSilently(conn, "alter table chat_session add key idx_session_kb (customer_id, kb_id, updated_at)");
        }
    }

    private boolean tableExists(Connection conn, String table) throws SQLException {
        try (ResultSet rs = conn.getMetaData().getTables(conn.getCatalog(), null, table, null)) {
            return rs.next();
        }
    }

    private boolean columnExists(Connection conn, String table, String column) throws SQLException {
        try (ResultSet rs = conn.getMetaData().getColumns(conn.getCatalog(), null, table, column)) {
            return rs.next();
        }
    }

    private void ensureChatMessageCitations(Connection conn) throws SQLException {
        if (!columnExists(conn, "chat_message", "citations")) {
            executeSilently(conn, "alter table chat_message add column citations json null comment '关联回答的引用证据 JSON' after trace_id");
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
        } catch (SQLException e) {
            log.warn("执行 SQL 失败：{}", e.getMessage());
        }
    }
}
