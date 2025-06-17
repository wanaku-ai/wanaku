package ai.wanaku.tool.http;

import ai.wanaku.api.exceptions.WanakuException;
import ai.wanaku.core.capabilities.tool.Client;
import jakarta.enterprise.context.ApplicationScoped;

import ai.wanaku.core.exchange.ParsedToolInvokeRequest;
import ai.wanaku.core.exchange.ToolInvokeRequest;
import org.apache.camel.ProducerTemplate;
import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.Map;

@ApplicationScoped
public class HttpClient implements Client {

    private static final Logger LOG = Logger.getLogger(HttpClient.class);

    private final ProducerTemplate producer;

    public HttpClient(ProducerTemplate producer) {
        this.producer = producer;
    }


    @Override
    public Object exchange(ToolInvokeRequest request) throws WanakuException {
        producer.start();

        ParsedToolInvokeRequest parsedRequest = ParsedToolInvokeRequest.parseRequest(request);

        LOG.infof("Invoking tool at URI: %s", parsedRequest.uri());


        Map<String,Object> headers = new HashMap<>(parsedRequest.headers());

        return producer.requestBodyAndHeaders(parsedRequest.uri(), parsedRequest.body(), headers, String.class);
    }
}
