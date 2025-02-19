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

package org.wanaku.routers.resolvers;

import java.io.File;
import java.util.List;
import java.util.Map;

import io.quarkiverse.mcp.server.ResourceContents;
import org.jboss.logging.Logger;
import org.wanaku.api.types.ResourceReference;
import org.wanaku.core.mcp.common.resolvers.ResourceResolver;
import org.wanaku.routers.proxies.ResourceProxy;

public class WanakuResourceResolver implements ResourceResolver {
    private static final Logger LOG = Logger.getLogger(WanakuResourceResolver.class);
    private final File indexFile;
    private final Map<String, ? extends ResourceProxy> proxies;

    public WanakuResourceResolver(File indexFile, Map<String, ? extends ResourceProxy> proxies) {
        this.indexFile = indexFile;
        this.proxies = proxies;
    }

    @Override
    public File indexLocation() {
        return indexFile;
    }

    @Override
    public List<ResourceContents> read(ResourceReference mcpResource) {
        ResourceProxy resourceProxy = proxies.get(mcpResource.getType());
        LOG.infof("Using the resource proxy %s to evaluate MCP uri %s", resourceProxy.name(), mcpResource);

        return resourceProxy.eval(mcpResource);
    }
}
