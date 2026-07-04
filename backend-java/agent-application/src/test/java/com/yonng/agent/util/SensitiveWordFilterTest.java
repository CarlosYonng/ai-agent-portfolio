package com.yonng.agent.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 敏感词过滤器单元测试。
 *
 * <p>验证 DFA 字典树的匹配逻辑，不依赖配置文件。</p>
 */
class SensitiveWordFilterTest {

    private SensitiveWordFilter filter;

    @BeforeEach
    void setUp() {
        filter = new SensitiveWordFilter();
        // 手动添加测试词，不依赖配置文件
        filter.addWord("色情");
        filter.addWord("毒品");
        filter.addWord("赌博");
        filter.addWord("枪支");
    }

    @Test
    void containsExactWord() {
        assertThat(filter.containsSensitiveWord("这是一个色情网站")).isTrue();
    }

    @Test
    void containsWordAtStart() {
        assertThat(filter.containsSensitiveWord("毒品交易是非法的")).isTrue();
    }

    @Test
    void containsWordAtEnd() {
        assertThat(filter.containsSensitiveWord("在线赌博")).isTrue();
    }

    @Test
    void noSensitiveWord() {
        assertThat(filter.containsSensitiveWord("今天天气真好")).isFalse();
    }

    @Test
    void emptyTextReturnsFalse() {
        assertThat(filter.containsSensitiveWord("")).isFalse();
    }

    @Test
    void nullTextReturnsFalse() {
        assertThat(filter.containsSensitiveWord(null)).isFalse();
    }

    @Test
    void skipsSpecialCharacters() {
        assertThat(filter.containsSensitiveWord("色%%情")).isTrue();
    }

    @Test
    void skipsSpacesBetweenChars() {
        assertThat(filter.containsSensitiveWord("毒 品")).isTrue();
    }

    @Test
    void skipsMixedPunctuation() {
        assertThat(filter.containsSensitiveWord("枪。支。弹。药")).isTrue();
    }

    @Test
    void findFirstReturnsMatchedWord() {
        assertThat(filter.findFirstSensitiveWord("在线赌博网站")).isEqualTo("赌博");
    }

    @Test
    void findFirstReturnsNullForCleanText() {
        assertThat(filter.findFirstSensitiveWord("正常聊天内容")).isNull();
    }

    @Test
    void replaceSensitiveWords() {
        String result = filter.replaceSensitiveWords("毒品害人不浅", '*');
        assertThat(result).isEqualTo("**害人不浅");
    }

    @Test
    void multipleWordsAllReplaced() {
        filter.addWord("害人");
        String result = filter.replaceSensitiveWords("毒品害人", '*');
        assertThat(result).isEqualTo("****");
    }
}
