package com.yonng.agent.dto;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 故障诊断证据。
 *
 * <p>不同 MCP 工具返回字段不同，这里用固定 source 加扩展字段承接，避免 API 方法签名直接暴露 Map。</p>
 */
public class IncidentEvidence {

    /** 证据来源工具，例如 search_logs、search_code、search_tickets。 */
    private String source;

    /** 工具返回的扩展字段，保持原始证据细节不丢失。 */
    private Map<String, Object> attributes = new LinkedHashMap<>();

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    @JsonAnyGetter
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, Object> attributes) {
        this.attributes = attributes;
    }

    /**
     * 接收不同证据源的动态字段。
     */
    @JsonAnySetter
    public void putAttribute(String name, Object value) {
        attributes.put(name, value);
    }
}
