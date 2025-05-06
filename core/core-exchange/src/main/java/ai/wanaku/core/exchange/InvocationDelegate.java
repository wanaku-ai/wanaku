package ai.wanaku.core.exchange;

/**
 * A delegate that can be used by services that invoke tools
 */
public interface InvocationDelegate extends ConfigurableDelegate, RegisterAware, PropertyProvider {

    /**
     * Invokes the tool
     * @param request the tool invocation request, including the body and parameters passed
     * @return A ToolInvokeReply instance with details about the tool execution status
     */
    ToolInvokeReply invoke(ToolInvokeRequest request);
}
