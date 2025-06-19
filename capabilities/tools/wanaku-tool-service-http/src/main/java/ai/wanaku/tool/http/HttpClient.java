package ai.wanaku.tool.http;

import ai.wanaku.api.exceptions.WanakuException;
import ai.wanaku.core.capabilities.tool.Client;
import jakarta.enterprise.context.ApplicationScoped;

import ai.wanaku.core.config.provider.api.ConfigResource;
import ai.wanaku.core.capabilities.common.ParsedToolInvokeRequest;
import ai.wanaku.core.exchange.ToolInvokeRequest;
import ai.wanaku.core.runtime.camel.CamelQueryParameterBuilder;
import org.apache.camel.ProducerTemplate;
import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.Map;

import static ai.wanaku.core.runtime.camel.CamelQueryHelper.safeLog;

@ApplicationScoped
public class HttpClient implements Client {

    private static final Logger LOG = Logger.getLogger(HttpClient.class);

    private final ProducerTemplate producer;

    public HttpClient(ProducerTemplate producer) {
        this.producer = producer;
    }

    @Override
    public Object exchange(ToolInvokeRequest request, ConfigResource configResource) throws WanakuException {
        producer.start();

        CamelQueryParameterBuilder parameterBuilder = new CamelQueryParameterBuilder(configResource);
        ParsedToolInvokeRequest parsedRequest =
                ParsedToolInvokeRequest.parseRequest(request.getUri(), request, parameterBuilder::build);

        LOG.infof("Invoking tool at URI: %s", safeLog(parsedRequest.uri()));

        Map<String,Object> headers = new HashMap<>(request.getHeadersMap());

        return producer.requestBodyAndHeaders(parsedRequest.uri(), parsedRequest.body(), headers, String.class);
    }



}
