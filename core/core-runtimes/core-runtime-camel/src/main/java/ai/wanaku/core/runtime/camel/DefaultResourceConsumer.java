package ai.wanaku.core.runtime.camel;

import jakarta.enterprise.context.ApplicationScoped;

import ai.wanaku.core.exchange.ResourceRequest;
import ai.wanaku.core.services.provider.ResourceConsumer;
import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;

/**
 * A simple consumer of resources implemented on top of Camel's ConsumerTemplate
 */
@ApplicationScoped
public class DefaultResourceConsumer implements ResourceConsumer {
    private final ConsumerTemplate consumer;

    /**
     * Creates a new instance of the resource consumer
     * @param camelContext the camel context
     */
    public DefaultResourceConsumer(CamelContext camelContext) {
        this.consumer = camelContext.createConsumerTemplate();
    }

    @Override
    public Object consume(String uri, ResourceRequest request) {
        try {
            consumer.start();
            return consumer.receiveBody(uri, 5000);
        } finally {
            consumer.stop();
        }
    }
}
