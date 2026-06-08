package com.yonng.agent.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ChatService 轻量单元测试。
 *
 * <p>这里先覆盖无数据库依赖的标题生成逻辑，保证会话列表展示稳定。</p>
 */
class ChatServiceTest {

    @Test
    void titleFromQuestionReturnsDefaultWhenQuestionIsBlank() {
        assertThat(ChatService.titleFromQuestion("  ")).isEqualTo("New Session");
    }

    @Test
    void titleFromQuestionKeepsShortQuestion() {
        assertThat(ChatService.titleFromQuestion("PAY_5001 是什么意思"))
                .isEqualTo("PAY_5001 是什么意思");
    }

    @Test
    void titleFromQuestionCutsLongQuestionToThirtyChars() {
        String title = ChatService.titleFromQuestion("这是一个非常长的企业知识库问题，用来验证会话标题不会撑开页面布局");

        assertThat(title).hasSize(30);
        assertThat(title).isEqualTo("这是一个非常长的企业知识库问题，用来验证会话标题不会撑开页面");
    }

    @Test
    void splitAnswerReturnsSingleEmptyChunkWhenAnswerIsBlank() {
        assertThat(ChatService.splitAnswer("", 24)).containsExactly("");
    }

    @Test
    void splitAnswerKeepsChunksWithinSize() {
        assertThat(ChatService.splitAnswer("PAY_5001 表示支付回调签名校验失败。", 8))
                .containsExactly("PAY_5001", " 表示支付回调签", "名校验失败。");
    }
}
