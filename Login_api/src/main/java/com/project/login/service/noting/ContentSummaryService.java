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

    /**
     * 提取完整文本内容（用于敏感词全文检查）
     * @param bytes 文件字节数组
     * @param filename 文件名（用于判断文件类型）
     * @return 完整文本内容
     */
    public String extractFullText(byte[] bytes, String filename) {
        if (filename == null) {
            throw new RuntimeException("文件名不能为空");
        }
        String lowerFilename = filename.toLowerCase();

        if (lowerFilename.endsWith(".md")) {
            return extractMarkdownFullText(bytes);
        } else if (lowerFilename.endsWith(".pdf")) {
            return extractPdfFullText(bytes);
        } else {
            throw new RuntimeException("不支持的文件类型: " + filename);
        }
    }

    /**
     * 提取 Markdown 完整文本
     */
    private String extractMarkdownFullText(byte[] bytes) {
        try {
            String content = new String(bytes, StandardCharsets.UTF_8);
            // 去掉 Markdown 标记，保留文本内容
            content = content.replaceAll("(?m)^#+\\s*", "");        // 去掉标题标记
            content = content.replaceAll("\\*|_|`|~", "");          // 去掉强调符号
            content = content.replaceAll("!\\[.*?\\]\\(.*?\\)", ""); // 去掉图片
            content = content.replaceAll("\\[.*?\\]\\(.*?\\)", "");  // 去掉链接
            content = content.replaceAll("\\s+", " ");              // 多空格归一化
            return content;
        } catch (Exception e) {
            throw new RuntimeException("解析 Markdown 文件失败", e);
        }
    }

    /**
     * 提取 PDF 完整文本
     */
    private String extractPdfFullText(byte[] bytes) {
        try (PDDocument document = PDDocument.load(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document).replaceAll("\\s+", " ");
            return text;
        } catch (Exception e) {
            throw new RuntimeException("解析 PDF 文件失败", e);
        }
    }
}
