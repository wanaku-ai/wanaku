package ai.wanaku.core.exchange;

/**
 * A delegate that can be used by services that invoke tools
 */
public interface InvocationDelegate extends RegisterAware, ProvisionAware {

    /**
     * Invokes the tool
     * @param request the tool invocation request, including the body and parameters passed
     * @return A ToolInvokeReply instance with details about the tool execution status
     */
    ai.wanaku.core.exchange.v1.ToolInvokeReply invoke(ai.wanaku.core.exchange.v1.ToolInvokeRequest request);
}
