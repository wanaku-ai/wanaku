package ai.wanaku.routing.service;

import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

import ai.wanaku.core.exchange.ParsedToolInvokeRequest;
import ai.wanaku.core.exchange.ToolInvokeRequest;
import ai.wanaku.core.services.routing.Client;
import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.ProducerTemplate;
import org.jboss.logging.Logger;


@ApplicationScoped
public class KafkaClient implements Client {
    private static final Logger LOG = Logger.getLogger(KafkaClient.class);

    private final ProducerTemplate producer;
    private final ConsumerTemplate consumer;

    public KafkaClient(CamelContext camelContext) {
        this.producer = camelContext.createProducerTemplate();
        this.consumer = camelContext.createConsumerTemplate();
    }

    @Override
    public Object exchange(ToolInvokeRequest request) {
        Map<String, String> serviceConfigurationsMap = request.getServiceConfigurationsMap();

        String bootstrapServers = serviceConfigurationsMap.get("bootstrapHost");
        ParsedToolInvokeRequest parsedRequest = ParsedToolInvokeRequest.parseRequest(request);
        String requestUri = String.format("%s?brokers=%s", parsedRequest.uri(), bootstrapServers);

        String replyToTopic = serviceConfigurationsMap.get("replyToTopic");
        String responseUri = String.format("kafka://%s?brokers=%s", replyToTopic, bootstrapServers);

        LOG.infof("Invoking tool at URI: %s", requestUri);
        try {
            producer.start();
            consumer.start();

            producer.sendBody(requestUri, parsedRequest.body());

            LOG.infof("Waiting for reply at at URI: %s", responseUri);
            return consumer.receiveBody(responseUri);
        } finally {
            producer.stop();
            consumer.stop();
        }
    }
}
