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
import org.wanaku.api.resolvers.ToolsResolver;
import org.wanaku.api.resolvers.util.NoopToolsResolver;
import org.wanaku.routers.camel.proxies.ToolsProxy;
import org.wanaku.routers.camel.proxies.tools.CamelEndpointProxy;
import org.wanaku.routers.camel.proxies.tools.CamelRouteProxy;
import org.wanaku.routers.camel.resolvers.CamelToolsResolver;
import picocli.CommandLine;

import static org.wanaku.api.resolvers.Resolver.DEFAULT_TOOLS_INDEX_FILE_NAME;

@ApplicationScoped
public class ToolsProvider extends AbstractProvider<ToolsProxy, ToolsResolver> {
    @Inject
    CommandLine.ParseResult parseResult;

    @Inject
    CamelContext camelContext;

    @Produces
    @Override
    ToolsResolver getResolver() {
        if (parseResult.isUsageHelpRequested() || parseResult.isVersionHelpRequested()) {
            return new NoopToolsResolver();
        }

        initializeCamel(camelContext);

        File resourcesIndexFile = initializeIndex();

        Map<String, ? extends ToolsProxy> proxies = loadProxies();
        return new CamelToolsResolver(resourcesIndexFile, proxies);
    }

    @Override
    protected File initializeIndex() {
        String indexPath = parseResult.matchedOptionValue("indexes-path", "${user.home}/.wanaku/router/")
                .replace("${user.home}", System.getProperty("user.home"));

        return initializeResourcesIndex(indexPath, DEFAULT_TOOLS_INDEX_FILE_NAME);
    }

    @Override
    public Map<String, ToolsProxy> loadProxies() {
        Map<String, ToolsProxy> proxies = new HashMap<>();

        proxies.put("endpoints", new CamelEndpointProxy(camelContext));
        proxies.put("routes", new CamelRouteProxy(camelContext));

        return proxies;

    }
}
