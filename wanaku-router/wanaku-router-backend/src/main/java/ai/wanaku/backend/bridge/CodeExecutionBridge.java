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

import ai.wanaku.backend.common.ToolCallEvent;
import ai.wanaku.backend.service.support.ServiceResolver;
import ai.wanaku.capabilities.sdk.api.exceptions.ServiceNotFoundException;
import ai.wanaku.capabilities.sdk.api.types.execution.CodeExecutionRequest;
import ai.wanaku.capabilities.sdk.api.types.providers.ServiceTarget;
import ai.wanaku.core.exchange.CodeExecutionReply;
import io.smallrye.reactive.messaging.MutinyEmitter;
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
    private final MutinyEmitter<ToolCallEvent> toolCallEventEmitter;

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
     * Creates a new CodeExecutionBridge with the specified service resolver, transport, and event emitter.
     *
     * @param serviceResolver the resolver for locating code execution services
     * @param transport the transport for gRPC communication
     * @param toolCallEventEmitter the emitter for tool call events (nullable)
     */
    public CodeExecutionBridge(
            ServiceResolver serviceResolver,
            WanakuBridgeTransport transport,
            MutinyEmitter<ToolCallEvent> toolCallEventEmitter) {
        this.serviceResolver = serviceResolver;
        this.transport = transport;
        this.toolCallEventEmitter = toolCallEventEmitter;
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
    public Iterator<CodeExecutionReply> executeCode(String engineType, String language, CodeExecutionRequest request) {
        LOG.infof("Executing code (engine=%s, language=%s)", engineType, language);

        // Resolve the service
        ServiceTarget service = resolveService(engineType, language);

        // Build the gRPC request
        ai.wanaku.core.exchange.CodeExecutionRequest grpcRequest = buildGrpcRequest(engineType, language, request);

        // Emit STARTED event
        ToolCallEvent startedEvent = null;
        if (toolCallEventEmitter != null) {
            startedEvent = emitStartedEvent(engineType, language, service, request);
        }

        final Instant startTime = Instant.now();
        final ToolCallEvent finalStartedEvent = startedEvent;

        try {
            // Execute via transport
            Iterator<CodeExecutionReply> replyIterator = transport.executeCode(grpcRequest, service);

            // Wrap the iterator to emit completion event when done or on close
            return new CloseableCodeExecutionIterator(replyIterator, finalStartedEvent, startTime, this);
        } catch (Exception e) {
            // Emit FAILED event
            if (toolCallEventEmitter != null && startedEvent != null) {
                long duration = Duration.between(startTime, Instant.now()).toMillis();
                emitFailedEvent(
                        startedEvent.getEventId(),
                        ToolCallEvent.ErrorCategory.SERVICE_UNAVAILABLE,
                        e.getMessage(),
                        duration);
            }
            throw e;
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
                CodeExecutionReply reply = delegate.next();
                if (reply.getIsError()) {
                    hasError = true;
                    lastErrorMessage = reply.getContentCount() > 0 ? reply.getContent(0) : "Code execution error";
                }
                return reply;
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
        if (toolCallEventEmitter == null || startedEvent == null) {
            return;
        }
        long duration = Duration.between(startTime, Instant.now()).toMillis();
        if (hasError) {
            emitFailedEvent(
                    startedEvent.getEventId(), ToolCallEvent.ErrorCategory.EXECUTION_ERROR, errorMessage, duration);
        } else {
            emitCompletedEvent(startedEvent.getEventId(), "Code execution completed", duration);
        }
    }

    /**
     * Resolves a code execution service by matching serviceType, serviceSubType, and serviceName.
     * <p>
     * This method uses the service resolver to locate the appropriate service
     * and throws an exception if no service is found.
     *
     * @param engineType the engine type (serviceSubType)
     * @param language the language (serviceName)
     * @return the resolved service target
     * @throws ServiceNotFoundException if no matching service is found
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
     * <p>
     * The code in the request is expected to be base64-encoded and will be
     * decoded before being sent to the code execution service.
     * </p>
     *
     * @param engineType the engine type
     * @param language the programming language
     * @param request the SDK code execution request
     * @return the gRPC code execution request
     */
    private ai.wanaku.core.exchange.CodeExecutionRequest buildGrpcRequest(
            String engineType, String language, CodeExecutionRequest request) {

        String uri = String.format("code-execution-engine://%s/%s", engineType, language);

        // Decode the base64-encoded code
        String decodedCode = decodeBase64Code(request.getCode());

        ai.wanaku.core.exchange.CodeExecutionRequest.Builder builder =
                ai.wanaku.core.exchange.CodeExecutionRequest.newBuilder()
                        .setUri(uri)
                        .setCode(decodedCode)
                        .setTimeout(request.getTimeout());

        // Convert List<String> arguments to Map<String, String> with indexed keys
        List<String> arguments = request.getArguments();
        if (arguments != null && !arguments.isEmpty()) {
            for (int i = 0; i < arguments.size(); i++) {
                builder.putArguments("arg" + i, arguments.get(i));
            }
        }

        // Add environment variables if present
        Map<String, String> environment = request.getEnvironment();
        if (environment != null && !environment.isEmpty()) {
            builder.putAllEnvironment(environment);
        }

        return builder.build();
    }

    /**
     * Decodes base64-encoded code to plain text.
     *
     * @param base64Code the base64-encoded code string
     * @return the decoded code as a UTF-8 string
     * @throws IllegalArgumentException if the input is not valid base64
     */
    private String decodeBase64Code(String base64Code) {
        if (base64Code == null || base64Code.isEmpty()) {
            return "";
        }

        try {
            byte[] decodedBytes = Base64.getDecoder().decode(base64Code);
            return new String(decodedBytes, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            LOG.warnf("Failed to decode base64 code, using raw value: %s", e.getMessage());
            // If decoding fails, assume the code is not base64-encoded and return as-is
            return base64Code;
        }
    }

    /**
     * Emits a STARTED event for a code execution call.
     * <p>
     * Note: Arguments are redacted to avoid exposing potentially sensitive data.
     * Only metadata about the arguments (count) is logged, not the actual values.
     * </p>
     */
    private ToolCallEvent emitStartedEvent(
            String engineType, String language, ServiceTarget service, CodeExecutionRequest request) {
        try {
            Map<String, String> arguments = new HashMap<>();
            arguments.put("engineType", engineType);
            arguments.put("language", language);
            // Redact raw argument values to avoid leaking secrets or large payloads.
            // Only emit safe metadata about the arguments.
            if (request.getArguments() != null && !request.getArguments().isEmpty()) {
                arguments.put("argCount", String.valueOf(request.getArguments().size()));
                arguments.put("argsRedacted", "true");
            }

            String toolName = String.format("code-execution/%s/%s", engineType, language);

            ToolCallEvent event = ToolCallEvent.started(
                    toolName,
                    "code-execution-engine",
                    "code-execution", // connectionId for code execution
                    service.getId(),
                    service.toAddress(),
                    arguments,
                    new HashMap<>(), // no headers for code execution
                    "[CODE]", // redact actual code
                    "",
                    "");

            if (toolCallEventEmitter.hasRequests()) {
                toolCallEventEmitter.sendAndForget(event);
            }
            return event;
        } catch (Exception e) {
            LOG.warnf(e, "Failed to emit STARTED event for code execution %s/%s", engineType, language);
            return null;
        }
    }

    /**
     * Emits a COMPLETED event for a successful code execution.
     */
    private void emitCompletedEvent(String eventId, String content, long duration) {
        try {
            ToolCallEvent event = ToolCallEvent.completed(eventId, content, duration);
            if (toolCallEventEmitter.hasRequests()) {
                toolCallEventEmitter.sendAndForget(event);
            }
        } catch (Exception e) {
            LOG.warnf(e, "Failed to emit COMPLETED event for %s", eventId);
        }
    }

    /**
     * Emits a FAILED event for a failed code execution.
     */
    private void emitFailedEvent(
            String eventId, ToolCallEvent.ErrorCategory category, String errorMessage, long duration) {
        try {
            ToolCallEvent event = ToolCallEvent.failed(eventId, category, errorMessage, null, duration);
            if (toolCallEventEmitter.hasRequests()) {
                toolCallEventEmitter.sendAndForget(event);
            }
        } catch (Exception e) {
            LOG.warnf(e, "Failed to emit FAILED event for %s", eventId);
        }
    }
}
