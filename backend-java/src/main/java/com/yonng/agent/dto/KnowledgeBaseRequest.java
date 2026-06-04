package com.yonng.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 创建知识库请求。
 *
 * <p>visibility 可以先使用 PRIVATE/PUBLIC，后续再扩展为部门级 ACL。</p>
 */
public class KnowledgeBaseRequest {

    /** 知识库所属租户。 */
    @NotNull
    private Long tenantId;

    /** 知识库名称，面向管理后台展示。 */
    @NotBlank
    private String name;

    /** 知识库说明，用于描述适用业务范围。 */
    private String description;

    /** 可见性，第一版支持 PRIVATE/PUBLIC。 */
    private String visibility = "PRIVATE";

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getVisibility() {
        return visibility;
    }

    public void setVisibility(String visibility) {
        this.visibility = visibility;
    }
}
