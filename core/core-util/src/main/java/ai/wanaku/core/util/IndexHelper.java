package ai.wanaku.core.util;

import ai.wanaku.api.types.ResourceReference;
import ai.wanaku.api.types.ToolReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;

/**
 * Helper class for dealing with the resources file
 */
public class IndexHelper {

    private IndexHelper() {
    }

    /**
     * Load an index
     *
     * @param indexFile
     * @return
     * @throws Exception
     */
    private static <T> List<T> loadIndex(File indexFile, Class<T> clazz) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(indexFile,
                objectMapper.getTypeFactory().constructCollectionType(List.class, clazz));
    }

    /**
     * Load an index
     *
     * @param indexURL
     * @return
     * @throws Exception
     */
    private static <T> List<T> loadIndex(URL indexURL, Class<T> clazz) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(indexURL,
                objectMapper.getTypeFactory().constructCollectionType(List.class, clazz));
    }

    private static <T> void saveIndex(File indexFile, List<T> resourceReferences) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();

        objectMapper
                .writerWithDefaultPrettyPrinter()
                .writeValue(indexFile, resourceReferences);
    }

    /**
     * Load an index of resources
     *
     * @param indexFile
     * @return
     * @throws Exception
     */
    static List<ResourceReference> loadResourcesIndex(File indexFile) throws Exception {
        return loadIndex(indexFile, ResourceReference.class);
    }

    /**
     * Saves an index to a file
     *
     * @param indexFile
     * @param resourceReferences
     * @throws IOException
     */
    public static void saveResourcesIndex(File indexFile, List<ResourceReference> resourceReferences) throws IOException {
        saveIndex(indexFile, resourceReferences);
    }

    /**
     * Load an index of tools
     *
     * @param indexFile
     * @return
     * @throws Exception
     */
    public static List<ToolReference> loadToolsIndex(File indexFile) throws Exception {
        return loadIndex(indexFile, ToolReference.class);
    }

    /**
     * Load an index of tools
     *
     * @param indexURL
     * @return
     * @throws Exception
     */
    public static List<ToolReference> loadToolsIndex(URL indexURL) throws Exception {
        return loadIndex(indexURL, ToolReference.class);
    }

    /**
     * Saves an index of tools to a file
     *
     * @param indexFile
     * @param toolReferences
     * @throws IOException
     */
    public static void saveToolsIndex(File indexFile, List<ToolReference> toolReferences) throws IOException {
        saveIndex(indexFile, toolReferences);
    }
}
