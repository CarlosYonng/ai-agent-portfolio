package com.yonng.agent.util;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * DFA 敏感词过滤器。
 *
 * <p>使用字典树（Trie）实现，支持：
 * <ul>
 *   <li>高速匹配（O(n) 时间复杂度，n 为输入文本长度）</li>
 *   <li>跳过中间特殊字符（如 "敏||感||词" 也能检测 "敏感词"）</li>
 *   <li>从配置文件加载词库</li>
 * </ul>
 * </p>
 */
@Component
public class SensitiveWordFilter {

    private static final Logger log = LoggerFactory.getLogger(SensitiveWordFilter.class);

    /** 跳过的字符：空格、标点、数字字母等不影响词义的字符 */
    private static final char[] SKIP_CHARS = {
        ' ', '\t', '\n', '\r', '~', '`', '!', '@', '#', '$', '%', '^', '&', '*',
        '(', ')', '-', '_', '+', '=', '[', ']', '{', '}', '|', '\\', ':', ';',
        '"', '\'', '<', '>', ',', '.', '?', '/', '★', '☆', '◆', '◇', '●', '○',
        '◎', '△', '▲', '※', '→', '←', '↑', '↓', '㊣', '№', '＠', '￣',
        '。', '，', '、', '；', '：', '？', '！', '…', '—', '·', 'ˉ', '¨',
        '‘', '’', '“', '”', '〔', '〕', '（', '）', '［', '］', '｛', '｝',
        '～', '′', '″', '〃', '々', '～'
    };

    /** 跳过的字符集合，用于快速判定 */
    private static final boolean[] SKIP_FLAGS = new boolean[Character.MAX_VALUE + 1];
    static {
        for (char c : SKIP_CHARS) {
            SKIP_FLAGS[c] = true;
        }
    }

    /** 字典树根节点 */
    private final TrieNode root = new TrieNode();

    /** 敏感词词库文件路径 */
    @Value("${agent.sensitive-words-path:classpath:sensitive-words.txt}")
    private String wordsPath;

    public SensitiveWordFilter() {
    }

    /**
     * 初始化：从配置文件加载敏感词到字典树。
     */
    @PostConstruct
    public void init() {
        try {
            Resource resource = new org.springframework.core.io.DefaultResourceLoader().getResource(wordsPath);
            if (!resource.exists()) {
                log.warn("敏感词文件不存在: {}", wordsPath);
                return;
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                int count = 0;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty() && !line.startsWith("#")) {
                        addWord(line);
                        count++;
                    }
                }
                log.info("敏感词过滤器初始化完成，共加载 {} 个敏感词", count);
            }
        } catch (Exception e) {
            log.warn("加载敏感词文件失败，过滤器将返回空结果: {}", e.getMessage());
        }
    }

    /**
     * 添加敏感词到字典树。
     */
    public void addWord(String word) {
        TrieNode node = root;
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            node.children.putIfAbsent(c, new TrieNode());
            node = node.children.get(c);
        }
        node.isEnd = true;
    }

    /**
     * 检查文本是否包含敏感词。
     *
     * @param text 输入文本
     * @return 如果包含敏感词返回 true，否则 false
     */
    public boolean containsSensitiveWord(String text) {
        return findFirstSensitiveWord(text) != null;
    }

    /**
     * 查找第一个命中的敏感词。
     *
     * @param text 输入文本
     * @return 命中的敏感词，未命中返回 null
     */
    public String findFirstSensitiveWord(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        String cleaned = text;

        for (int i = 0; i < cleaned.length(); i++) {
            TrieNode node = root;
            int matchLen = 0;
            int pos = i;

            while (pos < cleaned.length()) {
                char c = cleaned.charAt(pos);
                // 跳过特殊字符
                if (SKIP_FLAGS[c]) {
                    pos++;
                    continue;
                }
                TrieNode child = node.children.get(c);
                if (child == null) {
                    break;
                }
                node = child;
                matchLen++;
                pos++;

                if (node.isEnd && matchLen >= 1) {
                    return cleaned.substring(i, pos);
                }
            }
        }
        return null;
    }

    /**
     * 替换文本中的敏感词为 ***。
     */
    public String replaceSensitiveWords(String text, char replacementChar) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        StringBuilder result = new StringBuilder(text);
        for (int i = 0; i < result.length(); i++) {
            TrieNode node = root;
            int matchLen = 0;
            int pos = i;

            while (pos < result.length()) {
                char c = result.charAt(pos);
                if (SKIP_FLAGS[c]) {
                    pos++;
                    continue;
                }
                TrieNode child = node.children.get(c);
                if (child == null) break;
                node = child;
                matchLen++;
                pos++;
                if (node.isEnd && matchLen >= 1) {
                    for (int j = i; j < pos; j++) {
                        result.setCharAt(j, replacementChar);
                    }
                    break;
                }
            }
        }
        return result.toString();
    }

    private static class TrieNode {
        Map<Character, TrieNode> children = new HashMap<>();
        boolean isEnd = false;
    }
}
