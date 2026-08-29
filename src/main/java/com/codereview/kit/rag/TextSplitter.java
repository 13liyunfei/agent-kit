package com.codereview.kit.rag;

import java.util.ArrayList;
import java.util.List;

/**
 * 递归文本切分器：按分隔符优先级递归切块，块大小上限 + 相邻重叠。
 *
 * <p>对齐主流 RAG 的 chunk 策略：先按段落（\n\n）再按行再按句，保证语义完整。
 */
public class TextSplitter {

    private static final List<String> SEPARATORS = List.of("\n\n", "\n", "。", "！", "？", "；", ". ", "! ", "? ", "; ");

    private final int maxChunkSize;
    private final int overlap;

    public TextSplitter(int maxChunkSize, int overlap) {
        this.maxChunkSize = Math.max(16, maxChunkSize);
        this.overlap = Math.min(Math.max(0, overlap), maxChunkSize / 2);
    }

    public TextSplitter() {
        this(800, 80);
    }

    public List<String> split(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        return splitText(text.trim(), SEPARATORS, 0);
    }

    private List<String> splitText(String text, List<String> seps, int depth) {
        List<String> out = new ArrayList<>();
        if (text.length() <= maxChunkSize) {
            out.add(text);
            return out;
        }
        if (depth >= seps.size()) {
            return hardSplit(text);
        }
        String sep = seps.get(depth);
        String[] parts = text.split(java.util.regex.Pattern.quote(sep), -1);
        if (parts.length <= 1) {
            return splitText(text, seps, depth + 1);
        }
        StringBuilder chunk = new StringBuilder();
        for (String p : parts) {
            String candidate = chunk.isEmpty() ? p : chunk + sep + p;
            if (candidate.length() > maxChunkSize && !chunk.isEmpty()) {
                out.addAll(splitText(chunk.toString(), seps, depth + 1));
                chunk.setLength(0);
                chunk.append(p);
            } else {
                chunk.setLength(0);
                chunk.append(candidate);
            }
        }
        if (!chunk.isEmpty()) {
            out.addAll(splitText(chunk.toString(), seps, depth + 1));
        }
        return withOverlap(out);
    }

    private List<String> hardSplit(String text) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < text.length(); i += maxChunkSize - overlap) {
            out.add(text.substring(i, Math.min(text.length(), i + maxChunkSize)));
        }
        return out;
    }

    private List<String> withOverlap(List<String> chunks) {
        if (overlap <= 0 || chunks.size() <= 1) {
            return chunks;
        }
        List<String> out = new ArrayList<>();
        out.add(chunks.get(0));
        for (int i = 1; i < chunks.size(); i++) {
            String prev = chunks.get(i - 1);
            String tail = prev.length() <= overlap ? prev : prev.substring(prev.length() - overlap);
            out.add(tail + chunks.get(i));
        }
        return out;
    }
}
