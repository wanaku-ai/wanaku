package org.wanaku.routers.camel;

import java.io.File;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.wanaku.api.types.ResourceReference;

public class ResourcesHelper {
    public static List<ResourceReference> loadResources(File resourcesFile) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(resourcesFile,
                objectMapper.getTypeFactory().constructCollectionType(List.class, ResourceReference.class));
    }
}
