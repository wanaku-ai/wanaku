package ai.wanaku.core.services.provider;

import jakarta.enterprise.context.ApplicationScoped;

import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;

/**
 * A simple consumer of resources implemented on top of Camel's ConsumerTemplate
 */
@ApplicationScoped
public class DefaultResourceConsumer implements ResourceConsumer {
    private final ConsumerTemplate consumer;

    public DefaultResourceConsumer(CamelContext camelContext) {
        this.consumer = camelContext.createConsumerTemplate();
    }

    @Override
    public Object consume(String uri) {
        try {
            consumer.start();
            return consumer.receiveBody(uri, 5000);
        } finally {
            consumer.stop();
        }
    }
}
