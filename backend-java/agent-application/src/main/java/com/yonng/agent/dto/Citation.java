package com.yonng.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * RAG 答案引用证据。
 *
 * <p>对应 AI 服务返回的 citation 结构，前端用它展示来源标题、分数和片段预览。</p>
 *
 * @param chunkId 文档切片 ID，对应 Qdrant payload 或 MySQL chunk 主键
 * @param docId 文档 ID，用于后续跳转文档详情
 * @param title 证据所属文档标题
 * @param score 检索相关性分数
 * @param preview 证据片段预览
 */
public record Citation(
        @JsonProperty("chunk_id") String chunkId,
        @JsonProperty("doc_id") String docId,
        String title,
        Double score,
        String preview
) {
}
