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

package org.wanaku.routers.camel;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.apache.camel.CamelContext;
import org.wanaku.core.mcp.common.resolvers.ResourceResolver;
import org.wanaku.core.mcp.common.resolvers.util.NoopResourceResolver;
import org.wanaku.routers.camel.proxies.ResourceProxy;
import org.wanaku.routers.camel.proxies.resources.FileProxy;
import org.wanaku.routers.camel.resolvers.CamelResourceResolver;
import picocli.CommandLine;

import static org.wanaku.core.mcp.common.resolvers.Resolver.DEFAULT_RESOURCES_INDEX_FILE_NAME;

@ApplicationScoped
public class ResourcesProvider extends AbstractProvider<ResourceProxy, ResourceResolver> {
    @Inject
    CommandLine.ParseResult parseResult;

    @Inject
    CamelContext camelContext;

    @Produces
    @Override
    ResourceResolver getResolver() {
        if (parseResult.isUsageHelpRequested() || parseResult.isVersionHelpRequested()) {
            return new NoopResourceResolver();
        }

        File resourcesIndexFile = initializeIndex();

        initializeCamel(camelContext);
        Map<String, ? extends ResourceProxy> proxies = loadProxies();
        return new CamelResourceResolver(resourcesIndexFile, proxies);
    }


    @Override
    protected File initializeIndex() {
        String indexPath = parseResult.matchedOptionValue("indexes-path", "${user.home}/.wanaku/router/")
                .replace("${user.home}", System.getProperty("user.home"));

        return initializeResourcesIndex(indexPath, DEFAULT_RESOURCES_INDEX_FILE_NAME);
    }

    @Override
    public Map<String, ResourceProxy> loadProxies() {
        Map<String, ResourceProxy> proxies = new HashMap<>();

        proxies.put("file", new FileProxy(camelContext));

        return proxies;

    }
}
