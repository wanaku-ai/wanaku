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

package ai.wanaku.routers;

import java.io.File;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import ai.wanaku.core.mcp.common.resolvers.ResourceResolver;
import ai.wanaku.core.mcp.common.resolvers.util.NoopResourceResolver;
import ai.wanaku.routers.proxies.resources.ResourceAcquirerProxy;
import ai.wanaku.routers.resolvers.WanakuResourceResolver;
import picocli.CommandLine;

import static ai.wanaku.core.mcp.common.resolvers.Resolver.DEFAULT_RESOURCES_INDEX_FILE_NAME;

/**
 * A provider for resources resolvers
 */
@ApplicationScoped
public class ResourcesProvider extends AbstractProvider<ResourceResolver> {
    @Inject
    CommandLine.ParseResult parseResult;

    @Produces
    @Override
    ResourceResolver getResolver() {
        if (parseResult.isUsageHelpRequested() || parseResult.isVersionHelpRequested()) {
            return new NoopResourceResolver();
        }

        File resourcesIndexFile = initializeIndex();
        return new WanakuResourceResolver(resourcesIndexFile, new ResourceAcquirerProxy());
    }


    @Override
    protected File initializeIndex() {
        String indexPath = parseResult.matchedOptionValue("indexes-path", "${user.home}/.wanaku/router/")
                .replace("${user.home}", System.getProperty("user.home"));

        return initializeResourcesIndex(indexPath, DEFAULT_RESOURCES_INDEX_FILE_NAME);
    }
}
