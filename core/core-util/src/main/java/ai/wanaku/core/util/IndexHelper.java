package ai.wanaku.core.util;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import ai.wanaku.api.types.ResourceReference;
import ai.wanaku.api.types.ToolReference;

/**
 * Helper class for dealing with the resources file
 */
public class IndexHelper {


    /**
     * Load an index
     * @param indexFile
     * @return
     * @throws Exception
     */
    private static <T, Y> Map<T, Y> loadIndex(File indexFile, Class<T> clazzT, Class<Y> clazzY) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(indexFile,
                objectMapper.getTypeFactory().constructMapType(Map.class, clazzT, clazzY));
    }

    /**
     * Load an index
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
     * Saves an index of resources to a file
     * @param indexFile
     * @param resourceReferences
     * @throws IOException
     */
    public static <T> void saveIndex(File indexFile, List<T> resourceReferences) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();

        objectMapper
                .writerWithDefaultPrettyPrinter()
                .writeValue(indexFile, resourceReferences);
    }

    /**
     * Load an index of resources
     * @param indexFile
     * @return
     * @throws Exception
     */
    public static List<ResourceReference> loadResourcesIndex(File indexFile) throws Exception {
        return loadIndex(indexFile, ResourceReference.class);
    }

    /**
     * Saves an index to a file
     * @param indexFile
     * @param resourceReferences
     * @throws IOException
     */
    public static void saveResourcesIndex(File indexFile, List<ResourceReference> resourceReferences) throws IOException {
        saveIndex(indexFile, resourceReferences);
    }

    /**
     * Load an index of tools
     * @param indexFile
     * @return
     * @throws Exception
     */
    public static List<ToolReference> loadToolsIndex(File indexFile) throws Exception {
        return loadIndex(indexFile, ToolReference.class);
    }

    /**
     * Saves an index of tools to a file
     * @param indexFile
     * @param toolReferences
     * @throws IOException
     */
    public static void saveToolsIndex(File indexFile, List<ToolReference> toolReferences) throws IOException {
        saveIndex(indexFile, toolReferences);
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



    /**
     * Load an index of targets and their configurations
     * @param indexFile
     * @return
     * @throws Exception
     */
    public static <T> Map<String, T> loadTargetsIndex(File indexFile, Class<T> serviceClass) throws Exception {
        return loadIndex(indexFile, String.class, serviceClass);
    }
}
