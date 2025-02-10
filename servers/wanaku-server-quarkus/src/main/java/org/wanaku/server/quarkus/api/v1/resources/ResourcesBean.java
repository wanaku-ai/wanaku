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
import jakarta.inject.Inject;

import org.wanaku.api.resolvers.ResourceResolver;
import org.wanaku.api.types.ResourceReference;
import org.wanaku.core.util.IndexHelper;

@ApplicationScoped
public class ResourcesBean {
    @Inject
    ResourceResolver resourceResolver;

    public void expose(ResourceReference mcpResource) {
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
}
