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

package org.wanaku.routers.simple;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import org.wanaku.api.resolvers.ResourceResolver;
import org.wanaku.api.resolvers.ToolsResolver;
import org.wanaku.api.resolvers.util.NoopToolsResolver;
import picocli.CommandLine;

@ApplicationScoped
public class Providers {
    private static final Logger LOG = Logger.getLogger(Providers.class);

    @Inject
    CommandLine.ParseResult parseResult;

    @Inject
    org.wanaku.server.quarkus.McpResource mcpResource;

    @Produces
    ResourceResolver getResourceResolver() {
        var resourcesPath = parseResult.matchedOption("resources-path").getValue().toString();
        return new SimpleResourceResolver(resourcesPath);
    }

    @Produces
    ToolsResolver getToolsResolver() {
        return new NoopToolsResolver();
    }

}
