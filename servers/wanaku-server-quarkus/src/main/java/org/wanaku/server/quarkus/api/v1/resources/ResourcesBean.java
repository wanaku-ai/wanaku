package org.wanaku.server.quarkus.api.v1.resources;

import java.io.File;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.wanaku.api.resolvers.ResourceResolver;
import org.wanaku.api.types.ResourceReference;
import org.wanaku.core.util.ResourcesHelper;

@ApplicationScoped
public class ResourcesBean {
    @Inject
    ResourceResolver resourceResolver;

    public void expose(ResourceReference mcpResource) {
        File indexFile = resourceResolver.indexLocation();
        try {
            List<ResourceReference> resourceReferences = ResourcesHelper.loadIndex(indexFile);
            resourceReferences.add(mcpResource);
            ResourcesHelper.saveIndex(indexFile, resourceReferences);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
