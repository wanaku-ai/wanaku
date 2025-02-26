package ai.wanaku.routing.service;

import jakarta.enterprise.context.ApplicationScoped;

import ai.wanaku.core.exchange.ParsedToolInvokeRequest;
import ai.wanaku.core.exchange.ToolInvokeRequest;
import ai.wanaku.core.services.routing.Client;
import org.apache.camel.ProducerTemplate;
import org.jboss.logging.Logger;

@ApplicationScoped
public class HttpClient implements Client {
    private static final Logger LOG = Logger.getLogger(HttpClient.class);

    private final ProducerTemplate producer;

    public HttpClient(ProducerTemplate producer) {
        this.producer = producer;
    }

    @Override
    public Object exchange(ToolInvokeRequest request) {
        producer.start();

        ParsedToolInvokeRequest parsedRequest = ParsedToolInvokeRequest.parseRequest(request);

        LOG.infof("Invoking tool at URI: %s", parsedRequest.uri());

        String s;
        if (parsedRequest.body().isEmpty()) {
            s = producer.requestBody(parsedRequest.uri(), null, String.class);
        } else {
            s = producer.requestBody(parsedRequest.uri(), parsedRequest.body(), String.class);
        }
        return s;
    }
}
