package org.wanaku.core.util;

import java.io.File;
import java.io.IOException;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.wanaku.api.types.ResourceReference;

/**
 * Helper class for dealing with the resources file
 */
public class ResourcesHelper {

    /**
     * Load an index
     * @param indexFile
     * @return
     * @throws Exception
     */
    public static List<ResourceReference> loadIndex(File indexFile) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(indexFile,
                objectMapper.getTypeFactory().constructCollectionType(List.class, ResourceReference.class));
    }

    /**
     * Saves an index to a file
     * @param indexFile
     * @param resourceReferences
     * @throws IOException
     */
    public static void saveIndex(File indexFile, List<ResourceReference> resourceReferences) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();

        objectMapper
                 .writerWithDefaultPrettyPrinter()
                 .writeValue(indexFile, resourceReferences);
    }
}
