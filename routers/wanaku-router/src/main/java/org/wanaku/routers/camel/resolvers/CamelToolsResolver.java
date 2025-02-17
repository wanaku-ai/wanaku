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

package org.wanaku.routers.camel.resolvers;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jboss.logging.Logger;
import org.wanaku.api.exceptions.ToolNotFoundException;
import org.wanaku.api.types.McpTool;
import org.wanaku.api.types.McpToolStatus;
import org.wanaku.core.mcp.common.resolvers.ToolsResolver;
import org.wanaku.routers.camel.proxies.ToolsProxy;

public class CamelToolsResolver implements ToolsResolver {
    private static final Logger LOG = Logger.getLogger(CamelToolsResolver.class);
    private final File indexFile;
    private final Map<String, ? extends ToolsProxy> proxies;

    public CamelToolsResolver(File indexFile, Map<String, ? extends ToolsProxy> proxies) {
        this.indexFile = indexFile;
        this.proxies = proxies;
    }

    @Override
    public File indexLocation() {
        return indexFile;
    }

    @Override
    public List<McpTool> list() {
        List<McpTool> all = new ArrayList<>();
        List<? extends ToolsProxy> list = proxies.values().stream().toList();
        for (ToolsProxy proxy : list) {
            LOG.infof("Querying proxy %s for managed tools", proxy.name());
            all.addAll(proxy.list(indexLocation()));
        }

        return all;
    }

    @Override
    public McpTool find(String name) throws ToolNotFoundException {
        List<? extends ToolsProxy> list = proxies.values().stream().toList();
        for (ToolsProxy proxy : list) {
            LOG.infof("Querying proxy %s for managed tools", proxy.name());
            List<McpTool> managedTools = proxy.list(indexLocation());
            Optional<McpTool> first = managedTools.stream().filter(t -> t.name.equals(name)).findFirst();
            if (first.isPresent()) {
                return first.get();
            }
        }

        throw ToolNotFoundException.forName(name);
    }

    public ToolsProxy findProxy(String name) throws ToolNotFoundException {
        List<? extends ToolsProxy> list = proxies.values().stream().toList();
        for (ToolsProxy proxy : list) {
            LOG.infof("Querying proxy %s for managed tools", proxy.name());
            List<McpTool> managedTools = proxy.list(indexLocation());
            Optional<McpTool> first = managedTools.stream().filter(t -> t.name.equals(name)).findFirst();
            if (first.isPresent()) {
                return proxy;
            }
        }

        throw ToolNotFoundException.forName(name);
    }

    @Override
    public McpToolStatus call(McpTool tool, Map<String, Object> properties) {
        try {
            ToolsProxy proxy = findProxy(tool.name);
            return proxy.call(tool, properties);
        } catch (ToolNotFoundException e) {
            // NOTE: shouldn't happen ...
            throw new RuntimeException(e);
        }
    }
}
