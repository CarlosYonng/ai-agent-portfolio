package com.yonng.agent.service.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yonng.agent.domain.user.SysCustomer;
import com.yonng.agent.dto.user.CustomerRequest;
import com.yonng.agent.dto.user.CustomerResponse;
import com.yonng.agent.exception.BusinessException;
import com.yonng.agent.exception.ErrorCode;
import com.yonng.agent.mapper.user.SysCustomerMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;

/**
 * 客户管理服务。
 *
 * <p>客户是外部用户的数据隔离边界；邀请码只用于公开注册绑定客户，平台用户由后台创建。</p>
 */
@Service
public class CustomerService {

    private static final String INVITE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SysCustomerMapper customerMapper;

    public CustomerService(SysCustomerMapper customerMapper) {
        this.customerMapper = customerMapper;
    }

    public List<CustomerResponse> listCustomers() {
        return customerMapper.selectList(new LambdaQueryWrapper<SysCustomer>()
                        .orderByDesc(SysCustomer::getId))
                .stream()
                .map(CustomerResponse::from)
                .toList();
    }

    public CustomerResponse getCustomer(Long id) {
        SysCustomer customer = customerMapper.selectById(id);
        if (customer == null) {
            throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND);
        }
        return CustomerResponse.from(customer);
    }

    public CustomerResponse createCustomer(CustomerRequest request) {
        ensureCustomerNameAvailable(request.getName(), null);
        SysCustomer customer = new SysCustomer();
        customer.setName(request.getName());
        customer.setContactName(request.getContactName());
        customer.setContactEmail(request.getContactEmail());
        customer.setInviteCode(generateInviteCode());
        customer.setStatus(request.getStatus() != null ? request.getStatus() : "ACTIVE");
        try {
            customerMapper.insert(customer);
        } catch (DuplicateKeyException error) {
            throw duplicateCustomerError(error);
        }
        return CustomerResponse.from(customer);
    }

    public void updateCustomer(Long id, CustomerRequest request) {
        SysCustomer customer = customerMapper.selectById(id);
        if (customer == null) {
            throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND);
        }
        if (request.getName() != null) {
            ensureCustomerNameAvailable(request.getName(), id);
            customer.setName(request.getName());
        }
        if (request.getContactName() != null) customer.setContactName(request.getContactName());
        if (request.getContactEmail() != null) customer.setContactEmail(request.getContactEmail());
        if (request.getStatus() != null) customer.setStatus(request.getStatus());
        try {
            customerMapper.updateById(customer);
        } catch (DuplicateKeyException error) {
            throw duplicateCustomerError(error);
        }
    }

    public void deleteCustomer(Long id) {
        if (customerMapper.deleteById(id) == 0) {
            throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND);
        }
    }

    private String generateInviteCode() {
        for (int attempt = 0; attempt < 8; attempt++) {
            String code = "CUST-" + randomSegment(8);
            Long count = customerMapper.selectCount(new LambdaQueryWrapper<SysCustomer>()
                    .eq(SysCustomer::getInviteCode, code));
            if (count == 0) {
                return code;
            }
        }
        throw new BusinessException(ErrorCode.CUSTOMER_INVITE_CODE_GENERATE_FAILED);
    }

    private void ensureCustomerNameAvailable(String name, Long currentId) {
        if (name == null || name.isBlank()) {
            return;
        }
        LambdaQueryWrapper<SysCustomer> wrapper = new LambdaQueryWrapper<SysCustomer>()
                .eq(SysCustomer::getName, name);
        if (currentId != null) {
            wrapper.ne(SysCustomer::getId, currentId);
        }
        if (customerMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(ErrorCode.CUSTOMER_NAME_EXISTS);
        }
    }

    private BusinessException duplicateCustomerError(DuplicateKeyException error) {
        String message = error.getMessage() == null ? "" : error.getMessage();
        if (message.contains("uk_customer_name")) {
            return new BusinessException(ErrorCode.CUSTOMER_NAME_EXISTS);
        }
        if (message.contains("uk_customer_invite_code")) {
            return new BusinessException(ErrorCode.CUSTOMER_INVITE_CODE_GENERATE_FAILED);
        }
        return new BusinessException(ErrorCode.CUSTOMER_NAME_EXISTS);
    }

    private String randomSegment(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(INVITE_ALPHABET.charAt(RANDOM.nextInt(INVITE_ALPHABET.length())));
        }
        return builder.toString().toUpperCase(Locale.ROOT);
    }
}
