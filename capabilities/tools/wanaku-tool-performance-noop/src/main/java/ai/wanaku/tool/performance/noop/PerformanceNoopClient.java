package ai.wanaku.tool.performance.noop;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.logging.Logger;
import ai.wanaku.capabilities.sdk.config.provider.api.ConfigResource;
import ai.wanaku.core.capabilities.tool.Client;
import ai.wanaku.core.exchange.v1.ToolInvokeRequest;

@ApplicationScoped
public class PerformanceNoopClient implements Client {
    private static final Logger LOG = Logger.getLogger(PerformanceNoopClient.class);

    private static final String SAMPLE_TEXT = "1234567890";

    @Override
    public Object exchange(ToolInvokeRequest request, ConfigResource configResource) {
        LOG.debugf(
                "[%s-%d] Received request for tool invocation",
                Thread.currentThread().getName(), Thread.currentThread().threadId());
        return SAMPLE_TEXT;
    }
}
