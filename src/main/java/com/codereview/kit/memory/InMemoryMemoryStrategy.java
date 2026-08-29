package com.codereview.kit.memory;

import com.codereview.kit.extension.spi.MemoryStrategy;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存记忆实现（默认）：进程内 Map，适合单机 / 测试。
 */
public class InMemoryMemoryStrategy implements MemoryStrategy {

    private final Map<String, String> store = new ConcurrentHashMap<>();

    @Override
    public Optional<String> get(String key) {
        return Optional.ofNullable(store.get(key));
    }

    @Override
    public void put(String key, String value) {
        if (value == null) {
            store.remove(key);
        } else {
            store.put(key, value);
        }
    }

    public int size() {
        return store.size();
    }

    @Override
    public String name() {
        return "memory.in-memory";
    }

    @Override
    public int order() {
        return 100;
    }
}
