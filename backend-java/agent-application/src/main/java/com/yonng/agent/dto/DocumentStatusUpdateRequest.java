package com.yonng.agent.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 文档索引状态更新请求。
 *
 * <p>用于导入脚本或管理台回写文档处理结果，避免把状态流转混在文档元数据编辑里。</p>
 */
public class DocumentStatusUpdateRequest {

    /** PENDING/INDEXED/FAILED，表示文档是否完成切片和向量索引。 */
    @NotBlank
    private String status;

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
