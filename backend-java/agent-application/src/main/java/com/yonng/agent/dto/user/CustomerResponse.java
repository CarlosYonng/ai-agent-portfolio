package com.yonng.agent.dto.user;

import com.yonng.agent.domain.user.SysCustomer;

/**
 * 租户响应。
 */
public class CustomerResponse {

    private Long id;
    private String name;
    private String contactName;
    private String contactEmail;
    private String inviteCode;
    private String status;
    private String createdAt;

    public static CustomerResponse from(SysCustomer tenant) {
        CustomerResponse r = new CustomerResponse();
        r.id = tenant.getId();
        r.name = tenant.getName();
        r.contactName = tenant.getContactName();
        r.contactEmail = tenant.getContactEmail();
        r.inviteCode = tenant.getInviteCode();
        r.status = tenant.getStatus();
        r.createdAt = tenant.getCreatedAt() != null ? tenant.getCreatedAt().toString() : null;
        return r;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getContactName() { return contactName; }
    public void setContactName(String contactName) { this.contactName = contactName; }

    public String getContactEmail() { return contactEmail; }
    public void setContactEmail(String contactEmail) { this.contactEmail = contactEmail; }

    public String getInviteCode() { return inviteCode; }
    public void setInviteCode(String inviteCode) { this.inviteCode = inviteCode; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
