package ai.wanaku.tool.kafka;

import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

import ai.wanaku.core.exchange.ParsedToolInvokeRequest;
import ai.wanaku.core.exchange.ToolInvokeRequest;
import ai.wanaku.core.services.tool.Client;
import jakarta.inject.Inject;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.ProducerTemplate;
import org.jboss.logging.Logger;


@ApplicationScoped
public class KafkaClient implements Client {
    private static final Logger LOG = Logger.getLogger(KafkaClient.class);

    @Inject
    ProducerTemplate producer;

    @Inject
    ConsumerTemplate consumer;

    @Override
    public Object exchange(ToolInvokeRequest request) {
        Map<String, String> serviceConfigurationsMap = request.getServiceConfigurationsMap();

        String bootstrapServers = serviceConfigurationsMap.get("bootstrapHost");
        String requestTopic = serviceConfigurationsMap.get("requestTopic");
        ParsedToolInvokeRequest parsedRequest = ParsedToolInvokeRequest.parseRequest(request);
        String requestUri = String.format("kafka://%s?brokers=%s", requestTopic, bootstrapServers);

        String replyToTopic = serviceConfigurationsMap.get("replyToTopic");
        String responseUri = String.format("kafka://%s?brokers=%s", replyToTopic, bootstrapServers);

        LOG.infof("Invoking tool at URI: %s", requestUri);

        producer.sendBody(requestUri, parsedRequest.body());

        LOG.infof("Waiting for reply at at URI: %s", responseUri);
        return consumer.receiveBody(responseUri);

    }
}
