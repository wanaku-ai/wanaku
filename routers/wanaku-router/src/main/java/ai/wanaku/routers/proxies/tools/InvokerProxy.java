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

package ai.wanaku.routers.proxies.tools;

import java.util.Map;

import ai.wanaku.api.types.ToolReference;
import ai.wanaku.api.types.management.Configuration;
import ai.wanaku.api.types.management.Configurations;
import ai.wanaku.api.types.management.Service;
import ai.wanaku.core.exchange.InquireReply;
import ai.wanaku.core.exchange.InquireRequest;
import ai.wanaku.core.exchange.InquirerGrpc;
import ai.wanaku.core.exchange.ToolInvokeReply;
import ai.wanaku.core.exchange.ToolInvokeRequest;
import ai.wanaku.core.exchange.ToolInvokerGrpc;
import ai.wanaku.core.mcp.providers.ServiceRegistry;
import ai.wanaku.routers.proxies.ToolsProxy;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolResponse;
import org.jboss.logging.Logger;

/**
 * A proxy class for invoking tools
 */
public class InvokerProxy implements ToolsProxy {
    private static final Logger LOG = Logger.getLogger(InvokerProxy.class);

    @Override
    public ToolResponse call(ToolReference toolReference, ToolManager.ToolArguments toolArguments) {
        Service service = ServiceRegistry.getInstance().getEntryForService(toolReference.getType());
        if (service == null) {
            return ToolResponse.error("There is no host registered for service " + toolReference.getType());
        }

        LOG.infof("Invoking %s on %s", toolReference.getType(), service);
        try {
            final ToolInvokeReply invokeReply = invokeRemotely(toolReference, toolArguments, service);

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
            ToolReference toolReference, ToolManager.ToolArguments toolArguments, Service service) {
        ManagedChannel channel = ManagedChannelBuilder.forTarget(service.getTarget()).usePlaintext().build();

        Map<String, Configuration> configurations = service.getConfigurations().getConfigurations();
        Map<String, String> collect = Configurations.toStringMap(configurations);

        Request result = Request.newRequest(toolReference, toolArguments);
        ToolInvokeRequest toolInvokeRequest = ToolInvokeRequest.newBuilder()
                .setBody(result.body())
                .setUri(result.uri())
                .putAllServiceConfigurations(collect)
//                TODO
//                .getArgumentsMap().putAll(toolArguments.args())
                .build();

        ToolInvokerGrpc.ToolInvokerBlockingStub blockingStub = ToolInvokerGrpc.newBlockingStub(channel);
        return blockingStub.invokeTool(toolInvokeRequest);
    }

    @Override
    public Map<String, String> getServiceConfigurations(String target) {
        ManagedChannel channel = ManagedChannelBuilder.forTarget(target)
                .usePlaintext()
                .build();

        InquireRequest inquireRequest = InquireRequest.newBuilder().build();
        InquirerGrpc.InquirerBlockingStub blockingStub = InquirerGrpc.newBlockingStub(channel);
        InquireReply inquire = blockingStub.inquire(inquireRequest);
        return inquire.getServiceConfigurationsMap();
    }

    @Override
    public String name() {
        return "invoker";
    }
}
