package ai.wanaku.core.mcp.providers;

import java.util.List;

public interface Repository<T> {
    void save(String keyPrefix, String keySuffix, T data);

    void delete(String keyPrefix, String keySuffix);

    T get(String keyPrefix, String keySuffix);

    List<T> getAll(String keyPrefix);
}
