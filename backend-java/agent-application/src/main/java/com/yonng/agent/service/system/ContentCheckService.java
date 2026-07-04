package com.yonng.agent.service.system;

import com.yonng.agent.exception.BusinessException;
import com.yonng.agent.exception.ErrorCode;
import com.yonng.agent.util.SensitiveWordFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 内容安全检查服务。
 *
 * <p>在用户输入到达 AI 服务之前拦截敏感内容，返回明确的业务错误给前端。
 * 可在此扩展调用第三方审核 API（如腾讯天御、阿里云内容安全）。</p>
 */
@Service
public class ContentCheckService {

    private static final Logger log = LoggerFactory.getLogger(ContentCheckService.class);

    private final SensitiveWordFilter sensitiveWordFilter;

    public ContentCheckService(SensitiveWordFilter sensitiveWordFilter) {
        this.sensitiveWordFilter = sensitiveWordFilter;
    }

    /**
     * 检查文本是否包含敏感内容。
     *
     * <p>如果触发敏感词，会抛出 {@link BusinessException}，前端可据此显示友好提示。</p>
     *
     * @param text 用户输入文本
     * @param context 业务上下文描述（用于日志）
     * @throws BusinessException 如果内容敏感
     */
    public void check(String text, String context) {
        if (text == null || text.isBlank()) {
            return;
        }

        String matched = sensitiveWordFilter.findFirstSensitiveWord(text);
        if (matched != null) {
            log.warn("内容安全拦截 [{}] 命中敏感词: '{}', 原文片段: '{}'",
                    context, matched, text.substring(0, Math.min(text.length(), 50)));
            throw new BusinessException(ErrorCode.CONTENT_SENSITIVE_REJECTED,
                    "问题包含敏感内容，请重新输入");
        }
    }
}
