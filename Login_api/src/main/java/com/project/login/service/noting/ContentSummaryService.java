package com.project.login.service.noting;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

@Service
public class ContentSummaryService {
    /**
     * 通用方法，根据文件类型提取内容摘要
     */
    public String extractContentSummary(MultipartFile file) {
        String filename = Objects.requireNonNull(file.getOriginalFilename()).toLowerCase();

        if (filename.endsWith(".md")) {
            return extractMarkdownSummary(file);
        } else if (filename.endsWith(".pdf")) {
            return extractPdfSummary(file);
        } else {
            throw new RuntimeException("不支持的文件类型: " + filename);
        }
    }

    public String extractContentSummary(byte[] bytes, String filename) {
        String lower = Objects.requireNonNull(filename).toLowerCase();
        if (lower.endsWith(".md")) {
            return extractMarkdownSummary(bytes);
        } else if (lower.endsWith(".pdf")) {
            return extractPdfSummary(bytes);
        } else {
            throw new RuntimeException("不支持的文件类型: " + filename);
        }
    }

    public String extractFullText(byte[] bytes, String filename) {
        String lower = Objects.requireNonNull(filename).toLowerCase();
        if (lower.endsWith(".md")) {
            return extractMarkdownFull(bytes);
        } else if (lower.endsWith(".pdf")) {
            return extractPdfFull(bytes);
        } else {
            throw new RuntimeException("不支持的文件类型: " + filename);
        }
    }

    public String extractQuickFilterText(MultipartFile file) {
        String filename = Objects.requireNonNull(file.getOriginalFilename()).toLowerCase();
        try {
            if (filename.endsWith(".md")) {
                String content = new String(file.getBytes(), StandardCharsets.UTF_8);
                content = content.replaceAll("(?m)^#+\\s*", "");
                content = content.replaceAll("!\\[.*?\\]\\(.*?\\)", "");
                content = content.replaceAll("\\[.*?\\]\\(.*?\\)", "");
                content = content.replaceAll("\\s+", " ");
                return content.length() > 10000 ? content.substring(0, 10000) : content;
            } else if (filename.endsWith(".pdf")) {
                try (PDDocument document = PDDocument.load(file.getInputStream())) {
                    PDFTextStripper stripper = new PDFTextStripper();
                    stripper.setStartPage(1);
                    stripper.setEndPage(Math.min(2, document.getNumberOfPages()));
                    String text = stripper.getText(document).replaceAll("\\s+", " ");
                    return text.length() > 8000 ? text.substring(0, 8000) : text;
                }
            } else {
                throw new RuntimeException("不支持的文件类型: " + filename);
            }
        } catch (Exception e) {
            throw new RuntimeException("快速过滤文本提取失败", e);
        }
    }

    /**
     * 提取 Markdown 文件摘要
     */
    private String extractMarkdownSummary(MultipartFile file) {
        try {
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);

            // 去掉 Markdown 标记
            content = content.replaceAll("(?m)^#+\\s*", "");        // 去掉标题
            content = content.replaceAll("\\*|_|`|~", "");          // 去掉强调符号
            content = content.replaceAll("!\\[.*?\\]\\(.*?\\)", ""); // 去掉图片
            content = content.replaceAll("\\[.*?\\]\\(.*?\\)", "");  // 去掉链接
            content = content.replaceAll("\\s+", " ");              // 多空格归一化

            // 截取前 200 字作为摘要
            return content.length() > 200 ? content.substring(0, 200) : content;

        } catch (Exception e) {
            throw new RuntimeException("解析 Markdown 文件失败", e);
        }
    }

    private String extractMarkdownSummary(byte[] bytes) {
        try {
            String content = new String(bytes, StandardCharsets.UTF_8);
            content = content.replaceAll("(?m)^#+\\s*", "");
            content = content.replaceAll("\\*|_|`|~", "");
            content = content.replaceAll("!\\[.*?\\]\\(.*?\\)", "");
            content = content.replaceAll("\\[.*?\\]\\(.*?\\)", "");
            content = content.replaceAll("\\s+", " ");
            return content.length() > 200 ? content.substring(0, 200) : content;
        } catch (Exception e) {
            throw new RuntimeException("解析 Markdown 文件失败", e);
        }
    }

    /**
     * 提取 PDF 文件摘要
     */
    private String extractPdfSummary(MultipartFile file) {
        try (PDDocument document = PDDocument.load(file.getInputStream())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document).replaceAll("\\s+", " ");

            // 截取前 200 字作为摘要
            return text.length() > 200 ? text.substring(0, 200) : text;

        } catch (Exception e) {
            throw new RuntimeException("解析 PDF 文件失败", e);
        }
    }

    private String extractPdfSummary(byte[] bytes) {
        try (PDDocument document = PDDocument.load(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document).replaceAll("\\s+", " ");
            return text.length() > 200 ? text.substring(0, 200) : text;
        } catch (Exception e) {
            throw new RuntimeException("解析 PDF 文件失败", e);
        }
    }

    private String extractMarkdownFull(byte[] bytes) {
        try {
            String content = new String(bytes, StandardCharsets.UTF_8);
            content = content.replaceAll("(?m)^#+\\s*", "");
            content = content.replaceAll("\\*|_|`|~", "");
            content = content.replaceAll("!\\[.*?\\]\\(.*?\\)", "");
            content = content.replaceAll("\\[.*?\\]\\(.*?\\)", "");
            content = content.replaceAll("\\s+", " ");
            return content;
        } catch (Exception e) {
            throw new RuntimeException("解析 Markdown 文件失败", e);
        }
    }

    private String extractPdfFull(byte[] bytes) {
        try (PDDocument document = PDDocument.load(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document).replaceAll("\\s+", " ");
            return text;
        } catch (Exception e) {
            throw new RuntimeException("解析 PDF 文件失败", e);
        }
    }
}
