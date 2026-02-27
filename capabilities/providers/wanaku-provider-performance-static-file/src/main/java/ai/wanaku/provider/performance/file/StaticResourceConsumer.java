package ai.wanaku.provider.performance.file;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.logging.Logger;
import ai.wanaku.core.capabilities.provider.ResourceConsumer;
import ai.wanaku.core.exchange.v1.ResourceRequest;

@ApplicationScoped
public class StaticResourceConsumer implements ResourceConsumer {
    private static final Logger LOG = Logger.getLogger(StaticResourceConsumer.class);

    private static final String SAMPLE_TEXT = "1234567890";

    @Override
    public Object consume(String uri, ResourceRequest request) {

        LOG.debugf(
                "[%s-%d] Received request for URI: %s",
                Thread.currentThread().getName(), Thread.currentThread().threadId(), uri);
        return SAMPLE_TEXT;
    }
}
