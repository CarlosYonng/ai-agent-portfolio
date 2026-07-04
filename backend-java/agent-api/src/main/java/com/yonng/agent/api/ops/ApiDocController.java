package com.yonng.agent.api.ops;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 运维 API 文档导出接口。
 *
 * <p>调用方：接口调试工具和文档导入流程，不参与业务链路。</p>
 */
@RestController
@RequestMapping("/api/docs")
public class ApiDocController {

    @GetMapping(value = "/apifox-openapi.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public Resource apifoxOpenApi() {
        return new ClassPathResource("apifox-openapi.json");
    }
}
