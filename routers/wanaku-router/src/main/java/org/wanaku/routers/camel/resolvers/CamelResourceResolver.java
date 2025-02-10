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
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jboss.logging.Logger;
import org.wanaku.api.resolvers.AsyncRequestHandler;
import org.wanaku.api.resolvers.ResourceResolver;
import org.wanaku.api.types.McpRequestStatus;
import org.wanaku.api.types.McpResource;
import org.wanaku.api.types.McpResourceData;
import org.wanaku.routers.camel.proxies.ResourceProxy;

public class CamelResourceResolver implements ResourceResolver {
    private static final Logger LOG = Logger.getLogger(CamelResourceResolver.class);
    private final File indexFile;
    private final Map<String, ? extends ResourceProxy> proxies;

    public CamelResourceResolver(File indexFile, Map<String, ? extends ResourceProxy> proxies) {
        this.indexFile = indexFile;
        this.proxies = proxies;
    }

    @Override
    public File indexLocation() {
        return indexFile;
    }

    @Override
    public List<McpResource> list() {
        List<McpResource> allResources = new ArrayList<>();
        List<? extends ResourceProxy> list = proxies.values().stream().toList();
        for (ResourceProxy proxy : list) {
            LOG.infof("Querying proxy %s for managed resources", proxy.name());
            allResources.addAll(proxy.list(indexLocation()));
        }

        return allResources;
    }

    @Override
    public List<McpResourceData> read(String uri) {
        URI uriUri = URI.create(uri);
        String scheme = uriUri.getScheme();

        ResourceProxy resourceProxy = proxies.get(scheme);
        LOG.infof("Using the resource proxy %s to evaluate MCP uri %s", resourceProxy.name(), uri);

        return resourceProxy.eval(uri);
    }

    @Override
    public void subscribe(String uri, AsyncRequestHandler<McpRequestStatus<McpResourceData>> callback) {
        URI uriUri = URI.create(uri);
        String scheme = uriUri.getScheme();

        ResourceProxy resourceProxy = proxies.get(scheme);
        LOG.infof("Using the resource proxy %s to evaluate MCP uri %s", resourceProxy.name(), uri);

        resourceProxy.subscribe(uri, callback);
    }
}
