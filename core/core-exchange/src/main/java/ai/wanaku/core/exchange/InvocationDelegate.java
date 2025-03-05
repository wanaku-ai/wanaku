package ai.wanaku.core.exchange;

/**
 * A delegate that can be used by services that invoke tools
 */
public interface InvocationDelegate extends ConfigurableDelegate, RegisterAware {
    /**
     * Invokes the tool
     * @param request
     * @return
     */
    ToolInvokeReply invoke(ToolInvokeRequest request);
}
