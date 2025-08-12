package ai.wanaku.tool.kafka;

import static ai.wanaku.core.runtime.camel.CamelQueryHelper.safeLog;

import ai.wanaku.core.capabilities.common.ParsedToolInvokeRequest;
import ai.wanaku.core.capabilities.config.WanakuServiceConfig;
import ai.wanaku.core.capabilities.tool.Client;
import ai.wanaku.core.config.provider.api.ConfigResource;
import ai.wanaku.core.exchange.ToolInvokeRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.ProducerTemplate;
import org.jboss.logging.Logger;

@ApplicationScoped
public class KafkaClient implements Client {
    private static final Logger LOG = Logger.getLogger(KafkaClient.class);

    private final ProducerTemplate producer;
    private final ConsumerTemplate consumer;

    @Inject
    WanakuServiceConfig config;

    public KafkaClient(CamelContext camelContext) {
        this.producer = camelContext.createProducerTemplate();
        this.consumer = camelContext.createConsumerTemplate();
    }

    @Override
    public Object exchange(ToolInvokeRequest request, ConfigResource configResource) {
        final String requestUri = buildRequestUri(configResource);
        ParsedToolInvokeRequest parsedRequest =
                ParsedToolInvokeRequest.parseRequest(requestUri, request, configResource);

        final String responseUri = buildResponseUri(configResource);

        LOG.infof("Invoking tool at URI: %s", safeLog(parsedRequest.uri()));
        try {
            producer.start();
            consumer.start();

            producer.sendBody(parsedRequest.uri(), parsedRequest.body());

            LOG.infof("Waiting for reply at at URI: %s", responseUri);
            return consumer.receiveBody(responseUri);
        } finally {
            producer.stop();
            consumer.stop();
        }
    }

    private String buildRequestUri(ConfigResource configResource) {
        final String baseUri = config.baseUri();
        final String bootstrapHost = configResource.getConfig("bootstrapHost");
        final String requestTopic = configResource.getConfig("requestTopic");

        return String.format("%s//%s?brokers=%s", baseUri, requestTopic, bootstrapHost);
    }

    private String buildResponseUri(ConfigResource configResource) {
        final String baseUri = config.baseUri();
        final String bootstrapHost = configResource.getConfig("bootstrapHost");
        final String replyToTopic = configResource.getConfig("replyToTopic");

        return String.format("%s//%s?brokers=%s", baseUri, replyToTopic, bootstrapHost);
    }
}
