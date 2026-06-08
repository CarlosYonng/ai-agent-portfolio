package com.yonng.agent.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.OffsetDateTime;

/**
 * 知识库领域对象。
 *
 * <p>通过 MyBatis Plus 映射到 kb_space 表。</p>
 */
@TableName("kb_space")
public class KnowledgeBase {

    /** 知识库主键。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 知识库所属租户。 */
    private Long tenantId;

    /** 知识库名称，展示在管理台列表。 */
    private String name;

    /** 知识库业务说明，用于描述覆盖范围。 */
    private String description;

    /** PRIVATE/TEAM/PUBLIC，后续可演进为部门级 ACL。 */
    private String visibility;

    /** 知识库创建时间。 */
    private OffsetDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
