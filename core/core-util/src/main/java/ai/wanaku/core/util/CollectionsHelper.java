package ai.wanaku.core.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
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

    public static <T> List<T> join(List<T> list1, List<T> list2) {
        List<T> dest = new ArrayList<>(list1.size() + list2.size());
        dest.addAll(list1);
        dest.addAll(list2);

        return dest;
    }

    /**
     * Null-safe check if the specified {@code Collection} is not empty.
     * @param collection  the collection to check, may be null
     * @return  {@code true} if {@code collection} is not null and not empty, {@code false} otherwise.
     */
    public static boolean isNotEmpty(Collection<?> collection) {
        return !isEmpty(collection);
    }

    /**
     * Null-safe check if the specified {@link Collection} is empty.
     * @param collection the collection to check, may be null
     * @return {@code true} if the {@code collection} is null or empty, {@code false} otherwise.
     */
    public static boolean isEmpty(Collection<?> collection) {
        return collection == null || collection.isEmpty();
    }

    /**
     * Null-safe check if the specified {@link Map}  is not empty.
     * @param map the {@link Map} to check, may be null
     * @return {@code true } if {@code map} is not null and not empty, {@code false} otherwise.
     */
    public static boolean isNotEmpty(Map<?,?> map) {
        return !isEmpty(map);
    }

    /**
     * Null-safe check if the specified {@link Map}  is empty.
     * @param map the {@link Map} to check, may be null
     * @return {@code true } if {@code map} is null or empty, {@code false} otherwise.
     */
    public static boolean isEmpty(Map<?,?> map) {
        return map == null || map.isEmpty();
    }

}
