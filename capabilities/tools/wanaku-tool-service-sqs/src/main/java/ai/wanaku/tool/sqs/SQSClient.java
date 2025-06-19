package ai.wanaku.tool.sqs;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import ai.wanaku.core.capabilities.common.ParsedToolInvokeRequest;
import ai.wanaku.core.capabilities.config.WanakuServiceConfig;
import ai.wanaku.core.capabilities.tool.Client;
import ai.wanaku.core.config.provider.api.ConfigResource;
import ai.wanaku.core.exchange.ToolInvokeRequest;
import ai.wanaku.core.runtime.camel.CamelQueryParameterBuilder;
import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.ProducerTemplate;
import org.jboss.logging.Logger;

import static ai.wanaku.core.runtime.camel.CamelQueryHelper.safeLog;

@ApplicationScoped
public class SQSClient implements Client {
    private static final Logger LOG = Logger.getLogger(SQSClient.class);

    private final ProducerTemplate producer;
    private final ConsumerTemplate consumer;

    @Inject
    WanakuServiceConfig config;

    public SQSClient(CamelContext camelContext) {
        this.producer = camelContext.createProducerTemplate();
        this.consumer = camelContext.createConsumerTemplate();
    }

    @Override
    public Object exchange(ToolInvokeRequest request, ConfigResource configResource) {
        String baseRequestUri = buildRequestUri(configResource);

        CamelQueryParameterBuilder parameterBuilder = new CamelQueryParameterBuilder(configResource);
        ParsedToolInvokeRequest parsedRequest =
                ParsedToolInvokeRequest.parseRequest(baseRequestUri, request, parameterBuilder::build);

        String responseUri = buildResponseUri(configResource);

        LOG.infof("Invoking tool at URI: %s", safeLog(parsedRequest.uri()));
        try {
            producer.start();
            consumer.start();

            producer.sendBody(parsedRequest.uri(), request.getBody());

            LOG.infof("Waiting for reply at at URI: %s", responseUri);
            return consumer.receiveBody(responseUri);
        } finally {
            producer.stop();
            consumer.stop();
        }
    }

    private String buildRequestUri(ConfigResource configResource) {
        final String baseUri = config.baseUri();
        String requestQueue = configResource.getConfig("requestQueue");
        return String.format("%s:%s", baseUri, requestQueue);
    }

    private String buildResponseUri(ConfigResource configResource) {
        final String baseUri = config.baseUri();
        String responseQueue = configResource.getConfig("responseQueue");

        return String.format("%s:%s", baseUri, responseQueue);
    }
}