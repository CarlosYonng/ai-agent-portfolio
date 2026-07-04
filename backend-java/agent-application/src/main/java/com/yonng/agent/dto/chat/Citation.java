package com.yonng.agent.dto.chat;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * RAG 答案引用证据。
 *
 * <p>对应 AI 服务返回的 citation 结构，前端用它展示来源标题、分数和证据文本。</p>
 *
 * @param chunkId 文档切片 ID
 * @param docId 文档 ID，用于后续跳转文档详情
 * @param title 证据所属文档标题
 * @param score 检索相关性分数（Neo4j 路径无分数）
 * @param text 证据文本内容（Pattern A：Qdrant payload 存完整 chunk 文本）
 */
public record Citation(
        @JsonProperty("chunk_id") String chunkId,
        @JsonProperty("doc_id") String docId,
        String title,
        Double score,
        String text
) {
}
