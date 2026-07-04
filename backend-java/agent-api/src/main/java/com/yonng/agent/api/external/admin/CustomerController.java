package com.yonng.agent.api.external.admin;

import com.yonng.agent.dto.system.ApiResult;
import com.yonng.agent.dto.system.ApiStatus;
import com.yonng.agent.dto.user.CustomerRequest;
import com.yonng.agent.dto.user.CustomerResponse;
import com.yonng.agent.service.user.CustomerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 外部客户空间管理接口。
 *
 * <p>调用方：平台管理台。负责客户空间 CRUD，不供 AI 服务或导入脚本直接调用。</p>
 */
@RestController
@RequestMapping("/api/admin/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @GetMapping
    public ApiResult<List<CustomerResponse>> listCustomers() {
        return ApiResult.ok(customerService.listCustomers());
    }

    @GetMapping("/{id}")
    public ApiResult<CustomerResponse> getCustomer(@PathVariable Long id) {
        return ApiResult.ok(customerService.getCustomer(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResult<CustomerResponse> createCustomer(@Valid @RequestBody CustomerRequest request) {
        return ApiResult.ok(customerService.createCustomer(request));
    }

    @PutMapping("/{id}")
    public ApiStatus updateCustomer(@PathVariable Long id, @Valid @RequestBody CustomerRequest request) {
        customerService.updateCustomer(id, request);
        return ApiStatus.ok();
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ApiStatus deleteCustomer(@PathVariable Long id) {
        customerService.deleteCustomer(id);
        return ApiStatus.ok();
    }
}
