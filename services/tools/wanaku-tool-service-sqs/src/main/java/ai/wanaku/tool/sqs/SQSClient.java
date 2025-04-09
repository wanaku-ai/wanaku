package ai.wanaku.tool.sqs;

import jakarta.enterprise.context.ApplicationScoped;

import ai.wanaku.core.exchange.ParsedToolInvokeRequest;
import ai.wanaku.core.exchange.ToolInvokeRequest;
import ai.wanaku.core.services.tool.Client;

import jakarta.inject.Inject;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.ProducerTemplate;
import org.jboss.logging.Logger;

import java.util.Map;

@ApplicationScoped
public class SQSClient implements Client {
    private static final Logger LOG = Logger.getLogger(SQSClient.class);

    @Inject
    ProducerTemplate producer;
    @Inject
    ConsumerTemplate consumer;

    @Override
    public Object exchange(ToolInvokeRequest request) {
        Map<String, String> serviceConfigurationsMap = request.getServiceConfigurationsMap();

        String accessKey = serviceConfigurationsMap.get("accessKey");
        String secretKey = serviceConfigurationsMap.get("secretKey");
        String region = serviceConfigurationsMap.get("region");
        ParsedToolInvokeRequest parsedRequest = ParsedToolInvokeRequest.parseRequest(request);
        String requestUri = String.format("%s?accessKey=RAW(%s)&secretKey=RAW(%s)&region=%s", parsedRequest.uri(), accessKey, secretKey, region);

        String queueNameOrArn = serviceConfigurationsMap.get("queueNameOrArn");
        String responseUri = String.format("aws2-sqs://%s?accessKey=RAW(%s)&secretKey=RAW(%s)&region=%s", queueNameOrArn, accessKey, secretKey, region);

        LOG.infof("Invoking tool at URI: %s", requestUri);

        producer.sendBody(requestUri, parsedRequest.body());

        LOG.infof("Waiting for reply at at URI: %s", responseUri);
        return consumer.receiveBody(responseUri);

    }
}