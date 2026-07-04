package com.yonng.agent.dto.knowledge;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 文档登记请求。
 *
 * <p>第一版只登记文档元数据；真实文件上传可以后续增加 MultipartFile 接口。
 * customerId 由 JWT 自动注入。</p>
 */
public class DocumentCreateRequest {

    /** 文档所属知识库。 */
    @NotNull
    private Long kbId;

    /** 文档标题，通常来自文件名或用户填写的标题。 */
    @NotBlank
    private String title;

    /** markdown/pdf/docx 等来源类型，第一版默认 markdown。 */
    private String sourceType = "markdown";

    /** 原始文件路径或对象存储地址。 */
    private String sourceUri;

    public Long getKbId() {
        return kbId;
    }

    public void setKbId(Long kbId) {
        this.kbId = kbId;
    }

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
