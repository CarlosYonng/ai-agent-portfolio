package com.yonng.agent.dto;

/**
 * 知识库更新请求。
 *
 * <p>支持更新名称、描述、可见性。</p>
 */
public class KnowledgeBaseUpdateRequest {

    /** 知识库名称。 */
    private String name;

    /** 知识库说明。 */
    private String description;

    /** 可见性：PRIVATE/PUBLIC。 */
    private String visibility;

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
