package com.yonng.agent.dto;

/**
 * 文档元数据更新请求。
 *
 * <p>支持更新标题、来源类型、来源地址。不包含状态变更——状态更新走独立的 PATCH 接口。</p>
 */
public class DocumentUpdateRequest {

    /** 文档标题。 */
    private String title;

    /** markdown/pdf/docx 等来源类型。 */
    private String sourceType;

    /** 原始文件路径或对象存储地址。 */
    private String sourceUri;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceUri() {
        return sourceUri;
    }

    public void setSourceUri(String sourceUri) {
        this.sourceUri = sourceUri;
    }
}
