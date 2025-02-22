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

package org.wanaku.routers.proxies.tools;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolResponse;
import org.jboss.logging.Logger;
import org.wanaku.api.types.ToolReference;
import org.wanaku.core.exchange.ToolInvokeReply;
import org.wanaku.core.exchange.ToolInvokeRequest;
import org.wanaku.core.exchange.ToolInvokerGrpc;
import org.wanaku.core.mcp.providers.ServiceRegistry;
import org.wanaku.routers.proxies.ToolsProxy;

public class InvokerProxy implements ToolsProxy {
    private static final Logger LOG = Logger.getLogger(InvokerProxy.class);

    @Override
    public ToolResponse call(ToolReference toolReference, ToolManager.ToolArguments toolArguments) {
        String target = ServiceRegistry.getInstance().getHostForService(toolReference.getType());
        if (target == null) {
            return ToolResponse.error("There is no host registered for service " + toolReference.getType());
        }

        LOG.infof("Invoking %s on %s", toolReference.getType(), target);
        try {
            final ToolInvokeReply invokeReply = invokeRemotely(toolReference, toolArguments, target);

            if (invokeReply.getIsError()) {
                return ToolResponse.error(invokeReply.getContent());
            } else {
                return ToolResponse.success(invokeReply.getContent());
            }
        } catch (Exception e) {
            LOG.errorf(e, "Unable to call endpoint: %s", e.getMessage());
            return ToolResponse.error(e.getMessage());
        }
    }

    private static ToolInvokeReply invokeRemotely(
            ToolReference toolReference, ToolManager.ToolArguments toolArguments, String target) {
        ManagedChannel channel = ManagedChannelBuilder.forTarget(target)
                .usePlaintext()
                .build();

        Request result = Request.newRequest(toolReference, toolArguments);
        ToolInvokeRequest toolInvokeRequest = ToolInvokeRequest.newBuilder()
                .setBody(result.body())
                .setUri(result.uri())
//                TODO
//                .getArgumentsMap().putAll(toolArguments.args())
                .build();

        ToolInvokerGrpc.ToolInvokerBlockingStub blockingStub = ToolInvokerGrpc.newBlockingStub(channel);
        return blockingStub.invokeTool(toolInvokeRequest);
    }

    @Override
    public String name() {
        return "camel-invoker";
    }
}
