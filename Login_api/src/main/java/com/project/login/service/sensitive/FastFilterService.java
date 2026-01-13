package com.project.login.service.sensitive;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Slf4j
@Service
public class FastFilterService {

    @Value("${sensitive.keywords.path:}")
    private String externalPath;

    // 使用 Trie 树根节点代替 List，volatile 保证可见性
    private volatile TrieNode rootNode;

    @PostConstruct
    public void init() {
        // 构建新的树，避免影响现有请求
        TrieNode newRoot = new TrieNode();
        int count = 0;

        try {
            Set<String> loadedWords = new HashSet<>();

            // 1. 尝试加载外部文件
            if (externalPath != null && !externalPath.isEmpty()) {
                Path p = Path.of(externalPath);
                if (Files.exists(p)) {
                    try (BufferedReader br = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                        loadedWords.addAll(readLines(br));
                    }
                }
            }

            // 2. 兜底加载内部文件
            if (loadedWords.isEmpty()) {
                for (String fn : new String[]{"politics.txt", "sex.txt"}) {
                    ClassPathResource res = new ClassPathResource(fn);
                    if (res.exists()) {
                        try (InputStream is = res.getInputStream();
                             BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                            loadedWords.addAll(readLines(br));
                        }
                    }
                }
            }

            // 3. 构建 Trie 树
            for (String word : loadedWords) {
                addWordToTree(newRoot, word);
                count++;
            }

            // 4. 原子替换
            this.rootNode = newRoot;
            log.info("Sensitive filter initialized. Loaded {} keywords.", count);

        } catch (Exception e) {
            log.error("Failed to init sensitive filter", e);
            // 发生错误时确保 rootNode 不为 null，防止 NPE
            if (this.rootNode == null) {
                this.rootNode = new TrieNode();
            }
        }
    }

    /**
     * 核心匹配方法 (DFA算法)
     * 复杂度：O(TextLength)，与词库大小无关
     */
    public List<String> match(String text) {
        if (text == null || text.isEmpty()) return Collections.emptyList();
        
        List<String> hits = new ArrayList<>();
        TrieNode currentRoot = this.rootNode; // 获取当前时刻的根节点引用
        
        int length = text.length();
        
        // 遍历文本中的每一个字符作为"起始点"
        for (int i = 0; i < length; i++) {
            TrieNode node = currentRoot;
            int j = i;
            
            // 从起始点开始向后扫描
            while (j < length) {
                char c = text.charAt(j);

                // 1. 跳过特殊字符 (如空格、标点、Markdown符号)
                if (isSymbol(c)) {
                    j++;
                    // 如果这是匹配的第一步，主循环 i 也需要推进，避免重复判断符号
                    if (node == currentRoot) {
                        i++; 
                    }
                    continue;
                }

                // 2. 字符归一化 (转小写)
                c = Character.toLowerCase(c);
                
                // 3. 检查树中是否有子节点
                node = node.getSubNode(c);
                if (node == null) {
                    // 匹配断裂
                    break;
                }
                
                // 4. 检查是否到达敏感词结尾
                if (node.isEnd()) {
                    // 提取原文 (从 i 到 j+1)
                    hits.add(text.substring(i, j + 1));
                    if (hits.size() >= 5) return hits; // 限制最多返回5个
                    // 注意：这里不 break，继续往后看是否有更长的词
                }
                j++;
            }
        }
        return hits;
    }

    public void reload() {
        log.info("Reloading sensitive keywords...");
        init();
    }

    // --- 辅助逻辑 ---

    private void addWordToTree(TrieNode root, String word) {
        TrieNode node = root;
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            // 词库里的词也需要跳过符号，并转小写
            if (isSymbol(c)) continue; 
            c = Character.toLowerCase(c);
            
            node = node.addSubNode(c);
        }
        node.setEnd(true);
    }

    private List<String> readLines(BufferedReader br) throws Exception {
        List<String> list = new ArrayList<>();
        String line;
        while ((line = br.readLine()) != null) {
            String t = parseLine(line);
            if (!t.isEmpty()) list.add(t);
        }
        return list;
    }

    private String parseLine(String line) {
        if (line == null) return "";
        String s = line.trim();
        int idx = s.indexOf('→');
        if (idx >= 0) s = s.substring(idx + 1);
        return s.trim();
    }

    /**
     * 判断是否为特殊符号 (跳过 Markdown、标点、空白等)
     */
    private boolean isSymbol(char c) {
        // 0-9, a-z, A-Z 视为有效字符 (中文也是有效字符)
        // 其它视为符号
        return !Character.isLetterOrDigit(c) && !isChinese(c);
    }
    
    // 简单的中文区间判断
    private boolean isChinese(char c) {
        return c >= 0x4E00 && c <= 0x9FA5;
    }

    // --- Trie 树节点内部类 ---
    
    private static class TrieNode {
        private boolean isEnd = false;
        // 使用 HashMap 存储子节点，适应所有 Unicode 字符
        private Map<Character, TrieNode> subNodes = new HashMap<>();

        public void setEnd(boolean end) {
            isEnd = end;
        }

        public boolean isEnd() {
            return isEnd;
        }

        public TrieNode addSubNode(Character key) {
            return subNodes.computeIfAbsent(key, k -> new TrieNode());
        }

        public TrieNode getSubNode(Character key) {
            return subNodes.get(key);
        }
    }
}
