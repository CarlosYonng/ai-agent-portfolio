package com.yonng.agent.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 客户创建/更新请求。
 */
public class CustomerRequest {

    @NotBlank(message = "客户名称不能为空")
    @Size(max = 128, message = "名称最长 128 个字符")
    private String name;

    @Size(max = 128, message = "联系人最长 128 个字符")
    private String contactName;

    @Size(max = 256, message = "邮箱最长 256 个字符")
    private String contactEmail;

    private String status;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getContactName() { return contactName; }
    public void setContactName(String contactName) { this.contactName = contactName; }

    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
