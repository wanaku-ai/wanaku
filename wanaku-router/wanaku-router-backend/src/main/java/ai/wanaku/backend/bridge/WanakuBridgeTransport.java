package ai.wanaku.backend.bridge;

import ai.wanaku.backend.support.ProvisioningReference;
import ai.wanaku.capabilities.sdk.api.types.providers.ServiceTarget;
import ai.wanaku.core.exchange.CodeExecutionReply;
import ai.wanaku.core.exchange.CodeExecutionRequest;
import ai.wanaku.core.exchange.ResourceReply;
import ai.wanaku.core.exchange.ResourceRequest;
import ai.wanaku.core.exchange.ToolInvokeReply;
import ai.wanaku.core.exchange.ToolInvokeRequest;
import java.util.Iterator;

/**
 * Transport abstraction for Wanaku bridge operations.
 * <p>
 * This interface defines the contract for transport implementations that handle
 * communication between Wanaku bridges and remote services. It abstracts the
 * underlying transport protocol (e.g., gRPC, HTTP, WebSocket) from the bridge
 * business logic, enabling:
 * <ul>
 *   <li>Protocol independence - bridges work with any transport implementation</li>
 *   <li>Testability - easy to mock for unit testing</li>
 *   <li>Flexibility - support for multiple transport protocols</li>
 *   <li>Maintainability - clear separation of concerns</li>
 * </ul>
 * <p>
 * Implementations of this interface are responsible for:
 * <ul>
 *   <li>Managing connections to remote services</li>
 *   <li>Handling protocol-specific communication details</li>
 *   <li>Converting between transport-specific and domain types</li>
 *   <li>Error handling and retry logic</li>
 * </ul>
 *
 * @see ai.wanaku.backend.bridge.transports.grpc.GrpcTransport
 * @see ai.wanaku.backend.bridge.InvokerBridge
 * @see ai.wanaku.backend.bridge.ResourceAcquirerBridge
 */
public interface WanakuBridgeTransport {

    /**
     * Provisions configuration and secrets to a remote service.
     * <p>
     * This method sends configuration data and secrets to the specified service,
     * which stores them and returns URIs that can be used to reference them in
     * subsequent operations. The provisioning process ensures that sensitive
     * configuration and secrets are securely transmitted and stored.
     *
     * @param name the name identifier for the configuration and secrets
     * @param configData the configuration data to provision (may be null or empty)
     * @param secretsData the secrets data to provision (may be null or empty)
     * @param service the target service to provision to
     * @return a provisioning reference containing URIs and properties for accessing
     *         the provisioned configuration and secrets
     * @throws ai.wanaku.capabilities.sdk.api.exceptions.ServiceUnavailableException
     *         if the service cannot be reached or returns an error
     */
    ProvisioningReference provision(String name, String configData, String secretsData, ServiceTarget service);

    /**
     * Invokes a tool on a remote service.
     * <p>
     * This method sends a tool invocation request to the specified service and
     * returns the result. The request contains all necessary information for the
     * service to execute the tool, including arguments, headers, and references
     * to provisioned configuration and secrets.
     *
     * @param request the tool invocation request containing tool details and arguments
     * @param service the target service that will execute the tool
     * @return the tool invocation reply containing the execution result or error information
     * @throws ai.wanaku.capabilities.sdk.api.exceptions.ServiceUnavailableException
     *         if the service cannot be reached or returns an error
     */
    ToolInvokeReply invokeTool(ToolInvokeRequest request, ServiceTarget service);

    /**
     * Acquires a resource from a remote service.
     * <p>
     * This method sends a resource acquisition request to the specified service
     * and returns the resource content. The request specifies which resource to
     * acquire and may include references to provisioned configuration and secrets
     * needed to access the resource.
     *
     * @param request the resource acquisition request containing resource details
     * @param service the target service that provides the resource
     * @return the resource reply containing the resource content or error information
     * @throws ai.wanaku.capabilities.sdk.api.exceptions.ServiceUnavailableException
     *         if the service cannot be reached or returns an error
     */
    ResourceReply acquireResource(ResourceRequest request, ServiceTarget service);

    /**
     * Executes code on a remote code execution service via streaming.
     * <p>
     * This method sends a code execution request to the specified service and
     * returns an iterator over the streaming responses. The iterator will yield
     * execution output, status updates, and completion/error messages as they
     * arrive from the remote service.
     *
     * @param request the code execution request containing code and execution parameters
     * @param service the target service that will execute the code
     * @return an iterator over the streaming code execution replies
     * @throws ai.wanaku.capabilities.sdk.api.exceptions.ServiceUnavailableException
     *         if the service cannot be reached or returns an error
     */
    Iterator<CodeExecutionReply> executeCode(CodeExecutionRequest request, ServiceTarget service);
}
