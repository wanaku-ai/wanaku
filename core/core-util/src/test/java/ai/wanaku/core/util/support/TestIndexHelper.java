package ai.wanaku.core.util.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.util.Map;

public class TestIndexHelper {

    private TestIndexHelper() {}

    private static <T, Y> Map<T, Y> loadIndex(File indexFile, Class<T> clazzT, Class<Y> clazzY) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(indexFile,
                objectMapper.getTypeFactory().constructMapType(Map.class, clazzT, clazzY));
    }

    /**
     * Load an index of targets and their configurations
     * @param indexFile
     * @return
     * @throws Exception
     */
    public static <T> Map<String, T> loadTargetsIndex(File indexFile, Class<T> serviceClass) throws Exception {
        return loadIndex(indexFile, String.class, serviceClass);
    }

    /**
     * Saves an index of targets to a file
     * @param indexFile
     * @param map
     * @throws IOException
     */
    public static <T> void saveIndex(File indexFile, Map<T, ?> map) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();

        objectMapper
                .writerWithDefaultPrettyPrinter()
                .writeValue(indexFile, map);
    }

    /**
     * Saves an index of targets to a file
     * @param indexFile
     * @param targetsMap
     * @throws IOException
     */
    public static void saveTargetsIndex(File indexFile, Map<String, ?> targetsMap) throws IOException {
        saveIndex(indexFile, targetsMap);
    }
}
