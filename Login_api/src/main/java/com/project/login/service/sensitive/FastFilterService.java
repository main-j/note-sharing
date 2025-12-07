package com.project.login.service.sensitive;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FastFilterService {

    @Value("${sensitive.keywords.path:}")
    private String externalPath;

    private volatile List<String> keywords = Collections.emptyList();

    @PostConstruct
    public void init() {
        List<String> list = new ArrayList<>();
        try {
            if (externalPath != null && !externalPath.isEmpty()) {
                Path p = Path.of(externalPath);
                if (Files.exists(p)) {
                    try (BufferedReader br = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
                        String line;
                        while ((line = br.readLine()) != null) {
                            String t = parseLine(line);
                            if (!t.isEmpty()) list.add(normalizeKeyword(t));
                        }
                    }
                }
            }
            if (list.isEmpty()) {
                for (String fn : new String[]{"politics.txt", "sex.txt"}) {
                    ClassPathResource res = new ClassPathResource(fn);
                    if (res.exists()) {
                        try (InputStream is = res.getInputStream();
                             BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = br.readLine()) != null) {
                                String t = parseLine(line);
                                if (!t.isEmpty()) list.add(normalizeKeyword(t));
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
        keywords = list.stream().distinct().collect(Collectors.toList());
    }

    public List<String> match(String text) {
        if (text == null || text.isEmpty()) return Collections.emptyList();
        String norm = normalizeText(text);
        List<String> hits = new ArrayList<>();
        for (String k : keywords) {
            if (k.isEmpty()) continue;
            if (norm.contains(k)) {
                hits.add(k);
                if (hits.size() >= 5) break;
            }
        }
        return hits;
    }

    public void reload() {
        init();
    }

    private String parseLine(String line) {
        if (line == null) return "";
        String s = line.trim();
        int idx = s.indexOf('→');
        if (idx >= 0) s = s.substring(idx + 1);
        return s.trim();
    }

    private String normalizeText(String s) {
        String x = s.replaceAll("```[\\s\\S]*?```", " ");
        x = x.replaceAll("`[\\s\\S]*?`", " ");
        x = x.replaceAll("[\\p{Punct}\\s]+", "");
        x = x.toLowerCase(Locale.ROOT);
        return x;
    }

    private String normalizeKeyword(String s) {
        String x = s.replaceAll("[\\p{Punct}\\s]+", "");
        x = x.toLowerCase(Locale.ROOT);
        return x;
    }
}

