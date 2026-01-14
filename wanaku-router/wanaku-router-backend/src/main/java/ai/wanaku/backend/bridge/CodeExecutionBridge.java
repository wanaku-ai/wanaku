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

import ai.wanaku.backend.service.support.ServiceResolver;
import ai.wanaku.capabilities.sdk.api.exceptions.ServiceNotFoundException;
import ai.wanaku.capabilities.sdk.api.types.execution.CodeExecutionRequest;
import ai.wanaku.capabilities.sdk.api.types.providers.ServiceTarget;
import ai.wanaku.core.exchange.CodeExecutionReply;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
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
        this.serviceResolver = serviceResolver;
        this.transport = transport;
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

        // Execute via transport
        return transport.executeCode(grpcRequest, service);
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
}
