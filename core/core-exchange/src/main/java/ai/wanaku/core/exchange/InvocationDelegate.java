package ai.wanaku.core.exchange;

import java.util.Map;

/**
 * A delegate that can be used by services that invoke tools
 */
public interface InvocationDelegate extends ConfigurableDelegate, RegisterAware, PropertyProvider {

    /**
     * Invokes the tool
     * @param request
     * @return
     */
    ToolInvokeReply invoke(ToolInvokeRequest request);
}
