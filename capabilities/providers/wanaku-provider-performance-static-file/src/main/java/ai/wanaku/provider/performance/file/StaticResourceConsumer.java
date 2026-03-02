package ai.wanaku.provider.performance.file;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;
import ai.wanaku.core.capabilities.provider.ResourceConsumer;
import ai.wanaku.core.exchange.v1.ResourceRequest;

@ApplicationScoped
public class StaticResourceConsumer implements ResourceConsumer {
    private static final Logger LOG = Logger.getLogger(StaticResourceConsumer.class);

    private static final String SAMPLE_TEXT = "1234567890";

    @Inject
    WanakuPerformanceServiceConfig config;

    @Override
    public Object consume(String uri, ResourceRequest request) {
        int delay = config.delay();

        if (delay > 0) {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        LOG.debugf(
                "[%s-%d] Received request for URI: %s",
                Thread.currentThread().getName(), Thread.currentThread().threadId(), uri);
        return SAMPLE_TEXT;
    }
}
