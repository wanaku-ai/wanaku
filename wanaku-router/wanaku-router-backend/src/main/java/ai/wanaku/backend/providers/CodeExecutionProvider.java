/*
 * Copyright 2026 Wanaku AI
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ai.wanaku.backend.providers;

import ai.wanaku.backend.bridge.CodeExecutionBridge;
import ai.wanaku.backend.bridge.CodeExecutorBridge;
import ai.wanaku.backend.bridge.WanakuBridgeTransport;
import ai.wanaku.backend.bridge.transports.grpc.GrpcTransport;
import ai.wanaku.backend.common.ToolCallEvent;
import ai.wanaku.backend.service.support.FirstAvailable;
import ai.wanaku.backend.service.support.ServiceResolver;
import ai.wanaku.core.mcp.providers.ServiceRegistry;
import io.quarkus.arc.DefaultBean;
import io.smallrye.reactive.messaging.MutinyEmitter;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.OnOverflow;
import org.jboss.logging.Logger;
import picocli.CommandLine;

/**
 * A provider for code execution bridges.
 * <p>
 * This provider creates and configures the {@link CodeExecutorBridge} for use
 * in code execution operations. It follows the same pattern as {@link ToolsProvider}
 * and {@link ResourcesProvider}.
 * </p>
 */
@ApplicationScoped
public class CodeExecutionProvider {
    private static final Logger LOG = Logger.getLogger(CodeExecutionProvider.class);

    @Inject
    CommandLine.ParseResult parseResult;

    @Inject
    Instance<ServiceRegistry> serviceRegistryInstance;

    @Inject
    @Channel("tool-call-event")
    @OnOverflow(OnOverflow.Strategy.DROP)
    MutinyEmitter<ToolCallEvent> toolCallEventEmitter;

    ServiceRegistry serviceRegistry;

    @PostConstruct
    void init() {
        serviceRegistry = serviceRegistryInstance.get();
    }

    /**
     * Produces a CodeExecutorBridge instance for dependency injection.
     *
     * @return the configured CodeExecutorBridge
     */
    @Produces
    @DefaultBean
    @ApplicationScoped
    CodeExecutorBridge getCodeExecutorBridge() {
        if (parseResult.isUsageHelpRequested() || parseResult.isVersionHelpRequested()) {
            LOG.debug("Help requested, returning null bridge");
            return null;
        }

        LOG.info("Creating CodeExecutionBridge");
        ServiceResolver resolver = new FirstAvailable(serviceRegistry);
        WanakuBridgeTransport transport = new GrpcTransport();
        return new CodeExecutionBridge(resolver, transport, toolCallEventEmitter);
    }
}
