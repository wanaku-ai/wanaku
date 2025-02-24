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

package ai.wanaku.routers.proxies.resources;

import java.util.Collections;
import java.util.List;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.quarkiverse.mcp.server.ResourceContents;
import io.quarkiverse.mcp.server.TextResourceContents;
import org.jboss.logging.Logger;
import ai.wanaku.api.types.ResourceReference;
import ai.wanaku.core.exchange.ResourceAcquirerGrpc;
import ai.wanaku.core.exchange.ResourceReply;
import ai.wanaku.core.exchange.ResourceRequest;
import ai.wanaku.core.mcp.providers.ResourceRegistry;
import ai.wanaku.routers.proxies.ResourceProxy;

public class ResourceAcquirerProxy implements ResourceProxy {
    private static final Logger LOG = Logger.getLogger(ResourceAcquirerProxy.class);

    @Override
    public List<ResourceContents> eval(ResourceReference mcpResource) {
        String target = ResourceRegistry.getInstance().getHostForService(mcpResource.getType());
        if (target == null) {
            LOG.errorf("There is no host registered for type %s", mcpResource.getType());

            return Collections.emptyList();
        }

        LOG.infof("Requesting %s from %s", mcpResource.getName(), target);
        final ResourceReply reply = acquireRemotely(mcpResource, target);
        if (reply.getIsError()) {
            TextResourceContents textResourceContents =
                    new TextResourceContents("file://" + mcpResource.getLocation(), reply.getContent(), "text/plain");
            return List.of(textResourceContents);
        } else {
            TextResourceContents textResourceContents =
                    new TextResourceContents("file://" + mcpResource.getLocation(), reply.getContent(),
                            mcpResource.getMimeType());

            return List.of(textResourceContents);
        }
    }

    @Override
    public String name() {
        return "";
    }

    private ResourceReply acquireRemotely(ResourceReference mcpResource, String target) {
        ManagedChannel channel = ManagedChannelBuilder.forTarget(target)
                .usePlaintext()
                .build();

        ResourceRequest request = ResourceRequest
                .newBuilder()
                .setLocation(mcpResource.getLocation())
                .setType(mcpResource.getType())
                .setName(mcpResource.getName())
                .build();

        ResourceAcquirerGrpc.ResourceAcquirerBlockingStub blockingStub = ResourceAcquirerGrpc.newBlockingStub(channel);
        return blockingStub.resourceAcquire(request);
    }
}
