package com.codereview.kit.memory;

import com.codereview.kit.extension.spi.MemoryStrategy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * 文件记忆实现：每 key 一个文本文件（目录持久化，跨进程 / 重启存活）。
 *
 * <p>key 中的路径分隔符会被替换为下划线，防止目录穿越。
 */
public class FileMemoryStrategy implements MemoryStrategy {

    private final Path dir;

    public FileMemoryStrategy(Path dir) {
        this.dir = dir;
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建记忆目录: " + dir, e);
        }
    }

    @Override
    public Optional<String> get(String key) {
        Path f = file(key);
        if (!Files.isRegularFile(f)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readString(f, StandardCharsets.UTF_8));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    @Override
    public void put(String key, String value) {
        Path f = file(key);
        try {
            if (value == null) {
                Files.deleteIfExists(f);
            } else {
                Files.writeString(f, value, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new IllegalStateException("记忆写入失败: " + key, e);
        }
    }

    private Path file(String key) {
        String safe = key.replaceAll("[\\\\/:*?\"<>|]", "_");
        return dir.resolve(safe + ".txt");
    }

    @Override
    public String name() {
        return "memory.file";
    }

    @Override
    public int order() {
        return 100;
    }
}
