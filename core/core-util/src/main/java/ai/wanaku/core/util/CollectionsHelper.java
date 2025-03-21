package ai.wanaku.core.util;

import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Helper utilities for working with Java collections
 */
public class CollectionsHelper {
    private CollectionsHelper() {}

    /**
     * Convert a map of String keys and unspecified type to String (value must be
     * convertible to String using toString())
     * @param map the map of String keys and unspecified type
     * @return A Map of String keys and String values
     */
    public static Map<String, String> toStringStringMap(Map<String, ?> map) {
        return map.entrySet().stream()
                .filter(e -> e.getValue() != null)
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().toString()));
    }

    public static <K, V> Map<?, ?> sortedMapOf(Map<K, V> original) {
        return new TreeMap<>(original);
    }
}
