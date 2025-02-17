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

package org.wanaku.server.quarkus.api.v1.tools;

import java.io.File;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import io.quarkiverse.mcp.server.ToolManager;
import io.quarkus.runtime.StartupEvent;
import org.jboss.logging.Logger;
import org.wanaku.api.exceptions.ToolNotFoundException;
import org.wanaku.api.types.ToolReference;
import org.wanaku.core.mcp.common.Tool;
import org.wanaku.core.mcp.common.resolvers.ToolsResolver;
import org.wanaku.core.util.IndexHelper;

@ApplicationScoped
public class ToolsBean {
    private static final Logger LOG = Logger.getLogger(ToolsBean.class);

    @Inject
    ToolManager toolManager;

    @Inject
    ToolsResolver toolsResolver;

    public void add(ToolReference mcpResource) {
        try {
            registerTool(mcpResource);

            File indexFile = toolsResolver.indexLocation();
            try {
                List<ToolReference> toolReferences = IndexHelper.loadToolsIndex(indexFile);
                toolReferences.add(mcpResource);
                IndexHelper.saveToolsIndex(indexFile, toolReferences);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        } catch (ToolNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private void registerTool(ToolReference toolReference) throws ToolNotFoundException {
        System.out.println("Tool " + toolReference);
        LOG.debugf("Registering tool: %s", toolReference.getName());
        Tool tool = toolsResolver.resolve(toolReference);

        ToolManager.ToolDefinition toolDefinition = toolManager.newTool(toolReference.getName())
                .setDescription(toolReference.getDescription());

        final boolean required = isRequired(toolReference);

        Class<?> type = toType(toolReference);
        toolReference.getInputSchema().getProperties().forEach((key, value) -> {
            toolDefinition.addArgument(key, value.getDescription(), required, type);
        });

        toolDefinition
                .setHandler(ta -> tool.call(toolReference, ta))
                .register();
    }

    private static boolean isRequired(ToolReference toolReference) {
        boolean required = false;
        List<String> requiredList = toolReference.getInputSchema().getRequired();

        if (requiredList != null) {
            required = requiredList.contains(toolReference.getName());
        }
        return required;
    }

    public List<ToolReference> list() {
        File indexFile = toolsResolver.indexLocation();
        try {
            return IndexHelper.loadToolsIndex(indexFile);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // TODO:
    private Class<?> toType(ToolReference mcpResource) {
        return String.class;
    }

    void loadTools(@Observes StartupEvent ev) {
        File indexFile = toolsResolver.indexLocation();
        if (!indexFile.exists()) {
            LOG.warnf("Index file not found: %s", indexFile);
            return;
        }

        try {
            List<ToolReference> toolReferences = IndexHelper.loadToolsIndex(indexFile);
            for (ToolReference toolReference : toolReferences) {
                registerTool(toolReference);
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }
}
