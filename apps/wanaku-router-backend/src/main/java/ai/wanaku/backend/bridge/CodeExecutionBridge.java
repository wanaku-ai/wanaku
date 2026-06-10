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
package ai.wanaku.backend.bridge;

import java.io.Closeable;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.jboss.logging.Logger;
import ai.wanaku.backend.common.ToolCallEvent;
import ai.wanaku.backend.service.support.ServiceResolver;
import ai.wanaku.capabilities.sdk.api.exceptions.ServiceNotFoundException;
import ai.wanaku.capabilities.sdk.api.types.execution.CodeExecutionRequest;
import ai.wanaku.capabilities.sdk.api.types.providers.ServiceTarget;
import ai.wanaku.core.exchange.v1.CodeExecutionReply;
import ai.wanaku.core.util.StringHelper;

/**
 * Bridge implementation for code execution services via gRPC.
 * <p>
 * This bridge handles communication with remote code execution services.
 * It locates appropriate services based on serviceType, serviceSubType (engine-type),
 * and serviceName (language), then delegates execution to the transport layer.
 * </p>
 * <p>
 * This class follows the same pattern as {@link InvokerBridge} and
 * {@link ResourceAcquirerBridge}, using {@link ServiceResolver} for service
 * discovery and {@link WanakuBridgeTransport} for communication.
 * </p>
 */
public class CodeExecutionBridge implements CodeExecutorBridge {
    private static final Logger LOG = Logger.getLogger(CodeExecutionBridge.class);
    private static final String SERVICE_TYPE_CODE_EXECUTION = "code-execution-engine";

    private final ServiceResolver serviceResolver;
    private final WanakuBridgeTransport transport;
    private final EventNotifier eventNotifier;

    /**
     * Creates a new CodeExecutionBridge with the specified service resolver and transport.
     * <p>
     * This constructor is primarily intended for testing purposes, allowing
     * injection of a mock or custom transport implementation.
     *
     * @param serviceResolver the resolver for locating code execution services
     * @param transport the transport for gRPC communication
     */
    public CodeExecutionBridge(ServiceResolver serviceResolver, WanakuBridgeTransport transport) {
        this(serviceResolver, transport, null);
    }

    /**
     * Creates a new CodeExecutionBridge with the specified service resolver, transport, and event notifier.
     *
     * @param serviceResolver the resolver for locating code execution services
     * @param transport the transport for gRPC communication
     * @param eventNotifier the notifier for tool call events (nullable)
     */
    public CodeExecutionBridge(
            ServiceResolver serviceResolver, WanakuBridgeTransport transport, EventNotifier eventNotifier) {
        this.serviceResolver = serviceResolver;
        this.transport = transport;
        this.eventNotifier = eventNotifier;
    }

    /**
     * Executes code on the appropriate code execution service.
     * <p>
     * This method locates a code execution service matching the specified engine type
     * and language, then delegates execution to the transport layer.
     *
     * @param engineType the execution engine type (e.g., "jvm", "interpreted")
     * @param language the programming language (e.g., "java", "python")
     * @param request the SDK code execution request
     * @return an iterator over streaming code execution replies
     * @throws ServiceNotFoundException if no matching service is registered
     */
    @Override
    public Iterator<CodeExecutionReply> executeCode(
            String engineType, String language, CodeExecutionRequest request, String requestId) {
        LOG.infof("Executing code (engine=%s, language=%s)", engineType, language);

        ServiceTarget service = resolveService(engineType, language);

        ai.wanaku.core.exchange.v1.CodeExecutionRequest grpcRequest =
                buildGrpcRequest(engineType, language, request, requestId);

        RequestIdContext.setContext(requestId, null);

        ToolCallEvent startedEvent = null;
        if (eventNotifier != null) {
            startedEvent = emitStartedEvent(engineType, language, service, request);
        }

        final Instant startTime = Instant.now();
        final ToolCallEvent finalStartedEvent = startedEvent;

        try {
            Iterator<CodeExecutionReply> replyIterator = transport.executeCode(grpcRequest, service);

            return new CloseableCodeExecutionIterator(replyIterator, finalStartedEvent, startTime, this);
        } catch (Exception e) {
            if (eventNotifier != null && startedEvent != null) {
                long duration = Duration.between(startTime, Instant.now()).toMillis();
                eventNotifier.emitFailedEvent(
                        startedEvent.getEventId(),
                        ToolCallEvent.ErrorCategory.SERVICE_UNAVAILABLE,
                        e.getMessage(),
                        duration);
            }
            throw e;
        } finally {
            RequestIdContext.clear();
        }
    }

    /**
     * Closeable iterator wrapper that ensures completion/failure events are emitted
     * even when the iterator is not fully consumed or when exceptions occur during iteration.
     * <p>
     * Callers should use try-with-resources to ensure proper cleanup:
     * <pre>
     * try (var iterator = (CloseableCodeExecutionIterator) bridge.executeCode(...)) {
     *     while (iterator.hasNext()) {
     *         // process
     *     }
     * }
     * </pre>
     */
    public static class CloseableCodeExecutionIterator implements Iterator<CodeExecutionReply>, Closeable {
        private final Iterator<CodeExecutionReply> delegate;
        private final ToolCallEvent startedEvent;
        private final Instant startTime;
        private final CodeExecutionBridge bridge;
        private boolean completed = false;
        private boolean hasError = false;
        private String lastErrorMessage = null;

        CloseableCodeExecutionIterator(
                Iterator<CodeExecutionReply> delegate,
                ToolCallEvent startedEvent,
                Instant startTime,
                CodeExecutionBridge bridge) {
            this.delegate = delegate;
            this.startedEvent = startedEvent;
            this.startTime = startTime;
            this.bridge = bridge;
        }

        @Override
        public boolean hasNext() {
            try {
                boolean hasNext = delegate.hasNext();
                if (!hasNext && !completed) {
                    emitTerminalEvent();
                }
                return hasNext;
            } catch (Exception e) {
                // Emit FAILED event for exceptions during iteration
                if (!completed) {
                    hasError = true;
                    lastErrorMessage = e.getMessage() != null ? e.getMessage() : "Error during iteration";
                    emitTerminalEvent();
                }
                throw e;
            }
        }

        @Override
        public CodeExecutionReply next() {
            try {
                return delegate.next();
            } catch (Exception e) {
                if (!completed) {
                    hasError = true;
                    lastErrorMessage = e.getMessage() != null ? e.getMessage() : "Error during iteration";
                    emitTerminalEvent();
                }
                throw e;
            }
        }

        /**
         * Closes this iterator and emits a terminal event if not already done.
         * This ensures that even if the caller abandons iteration early,
         * a COMPLETED or FAILED event is still emitted.
         */
        @Override
        public void close() {
            if (!completed) {
                emitTerminalEvent();
            }
        }

        private void emitTerminalEvent() {
            completed = true;
            bridge.emitCompletionEvent(startedEvent, startTime, hasError, lastErrorMessage);
        }
    }

    private void emitCompletionEvent(
            ToolCallEvent startedEvent, Instant startTime, boolean hasError, String errorMessage) {
        if (eventNotifier == null || startedEvent == null) {
            return;
        }
        long duration = Duration.between(startTime, Instant.now()).toMillis();
        if (hasError) {
            eventNotifier.emitFailedEvent(
                    startedEvent.getEventId(), ToolCallEvent.ErrorCategory.EXECUTION_ERROR, errorMessage, duration);
        } else {
            eventNotifier.emitCompletedEvent(startedEvent.getEventId(), "Code execution completed", duration);
        }
    }

    /**
     * Resolves a code execution service by matching serviceType, serviceSubType, and serviceName.
     */
    private ServiceTarget resolveService(String engineType, String language) {
        LOG.debugf(
                "Resolving code execution service (serviceType=%s, serviceSubType=%s, serviceName=%s)",
                SERVICE_TYPE_CODE_EXECUTION, engineType, language);

        ServiceTarget service = serviceResolver.resolveCodeExecution(SERVICE_TYPE_CODE_EXECUTION, engineType, language);

        if (service == null) {
            throw new ServiceNotFoundException(String.format(
                    "No code execution service found for engine '%s' and language '%s'", engineType, language));
        }

        LOG.debugf("Resolved service: %s", service.toAddress());
        return service;
    }

    /**
     * Builds a gRPC CodeExecutionRequest from the SDK request.
     */
    private ai.wanaku.core.exchange.v1.CodeExecutionRequest buildGrpcRequest(
            String engineType, String language, CodeExecutionRequest request, String requestId) {

        String uri = String.format("code-execution-engine://%s/%s", engineType, language);

        String decodedCode = decodeBase64Code(request.getCode());

        ai.wanaku.core.exchange.v1.CodeExecutionRequest.Builder builder =
                ai.wanaku.core.exchange.v1.CodeExecutionRequest.newBuilder()
                        .setUri(uri)
                        .setCode(decodedCode)
                        .setTimeout(request.getTimeout())
                        .setRequestId(requestId != null ? requestId : "");

        List<String> arguments = request.getArguments();
        if (arguments != null && !arguments.isEmpty()) {
            for (int i = 0; i < arguments.size(); i++) {
                builder.putArguments("arg" + i, arguments.get(i));
            }
        }

        Map<String, String> environment = request.getEnvironment();
        if (environment != null && !environment.isEmpty()) {
            builder.putAllEnvironment(environment);
        }

        return builder.build();
    }

    private String decodeBase64Code(String base64Code) {
        if (StringHelper.isEmpty(base64Code)) {
            return "";
        }

        try {
            byte[] decodedBytes = Base64.getDecoder().decode(base64Code);
            return new String(decodedBytes, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            LOG.warnf("Failed to decode base64 code, using raw value: %s", e.getMessage());
            return base64Code;
        }
    }

    private ToolCallEvent emitStartedEvent(
            String engineType, String language, ServiceTarget service, CodeExecutionRequest request) {
        try {
            Map<String, String> arguments = new HashMap<>();
            arguments.put("engineType", engineType);
            arguments.put("language", language);
            if (request.getArguments() != null && !request.getArguments().isEmpty()) {
                arguments.put("argCount", String.valueOf(request.getArguments().size()));
                arguments.put("argsRedacted", "true");
            }

            String toolName = String.format("code-execution/%s/%s", engineType, language);

            ToolCallEvent event = ToolCallEvent.started(
                    toolName,
                    "code-execution-engine",
                    "code-execution",
                    service.getId(),
                    service.toAddress(),
                    arguments,
                    new HashMap<>(),
                    "[CODE]",
                    "",
                    "");

            return eventNotifier.emit(event);
        } catch (Exception e) {
            LOG.warnf(e, "Failed to emit STARTED event for code execution %s/%s", engineType, language);
            return null;
        }
    }
}
