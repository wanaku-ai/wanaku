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

package ai.wanaku.server.quarkus.api.v1.management.targets;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import io.quarkus.runtime.StartupEvent;
import org.jboss.logging.Logger;
import ai.wanaku.core.mcp.common.resolvers.ResourceResolver;
import ai.wanaku.core.mcp.common.resolvers.ToolsResolver;
import ai.wanaku.core.mcp.providers.ResourceRegistry;
import ai.wanaku.core.mcp.providers.ServiceRegistry;
import ai.wanaku.core.util.IndexHelper;

@ApplicationScoped
public class TargetsBean {
    private static final Logger LOG = Logger.getLogger(TargetsBean.class);

    @Inject
    ResourceResolver resourceResolver;

    @Inject
    ToolsResolver toolsResolver;

    public void toolsLink(String service, String target) throws IOException {
        ServiceRegistry.getInstance().link(service, target);
        IndexHelper.saveTargetsIndex(toolsResolver.targetsIndexFile(), ServiceRegistry.getInstance().getEntries());
    }

    public void toolsUnlink(String service) throws IOException {
        ServiceRegistry.getInstance().unlink(service);
        IndexHelper.saveTargetsIndex(toolsResolver.targetsIndexFile(), ServiceRegistry.getInstance().getEntries());
    }

    public Map<String,String> toolList() {
        return ServiceRegistry.getInstance().getEntries();
    }

    public void resourcesLink(String service, String target) throws IOException {
        ResourceRegistry.getInstance().link(service, target);
        IndexHelper.saveTargetsIndex(resourceResolver.targetsIndexFile(), ResourceRegistry.getInstance().getEntries());
    }

    public void resourcesUnlink(String service) throws IOException {
        ResourceRegistry.getInstance().unlink(service);
        IndexHelper.saveTargetsIndex(resourceResolver.targetsIndexFile(), ResourceRegistry.getInstance().getEntries());
    }

    public Map<String,String> resourcesList() {
        return ResourceRegistry.getInstance().getEntries();
    }

    void loadResources(@Observes StartupEvent ev) {
        var resourcesMap = doLoad("Resources", resourceResolver.targetsIndexFile());

        try {
            for (var entry : resourcesMap.entrySet()) {
                resourcesLink(entry.getKey(), entry.getValue());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        var toolsMap = doLoad("Tools", toolsResolver.targetsIndexFile());

        try {
            for (var entry : toolsMap.entrySet()) {
                toolsLink(entry.getKey(), entry.getValue());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Map<String, String> doLoad(String name, File indexFile) {
        if (!indexFile.exists()) {
            LOG.warnf("%s targets index file not found: %s", name, indexFile);
            return Map.of();
        }

        try {
            return IndexHelper.loadTargetsIndex(indexFile);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
