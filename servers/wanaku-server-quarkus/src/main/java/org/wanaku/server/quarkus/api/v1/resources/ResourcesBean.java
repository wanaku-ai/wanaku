/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wanaku.server.quarkus.api.v1.resources;

import java.io.File;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import io.quarkiverse.mcp.server.ResourceManager;
import io.quarkiverse.mcp.server.ResourceResponse;
import io.quarkus.runtime.StartupEvent;
import org.jboss.logging.Logger;
import org.wanaku.api.types.ResourceReference;
import org.wanaku.core.mcp.common.resolvers.ResourceResolver;
import org.wanaku.core.util.IndexHelper;

@ApplicationScoped
public class ResourcesBean {
    private static final Logger LOG = Logger.getLogger(ResourcesBean.class);

    @Inject
    ResourceManager resourceManager;

    @Inject
    ResourceResolver resourceResolver;

    public void expose(ResourceReference mcpResource) {
        doExposeResource(mcpResource);

        File indexFile = resourceResolver.indexLocation();
        try {
            List<ResourceReference> resourceReferences = IndexHelper.loadResourcesIndex(indexFile);
            resourceReferences.add(mcpResource);
            IndexHelper.saveResourcesIndex(indexFile, resourceReferences);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public List<ResourceReference> list() {
        File indexFile = resourceResolver.indexLocation();
        try {
            return IndexHelper.loadResourcesIndex(indexFile);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    void loadResources(@Observes StartupEvent ev) {
        File indexFile = resourceResolver.indexLocation();
        if (!indexFile.exists()) {
            LOG.warnf("Index file not found: %s", indexFile);
            return;
        }

        try {
            List<ResourceReference> resourceReferences = IndexHelper.loadResourcesIndex(indexFile);

            for (ResourceReference resourceReference : resourceReferences) {
                doExposeResource(resourceReference);
            }

            IndexHelper.saveResourcesIndex(indexFile, resourceReferences);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void doExposeResource(ResourceReference resourceReference) {
        LOG.debugf("Exposing resource: %s", resourceReference.getName());
        resourceManager.newResource(resourceReference.getName())
                .setUri(resourceReference.getLocation())
                .setMimeType(resourceReference.getMimeType())
                .setDescription(resourceReference.getDescription())
                .setHandler(
                        args -> new ResourceResponse(
                                resourceResolver.read(resourceReference)))
                .register();
    }
}
