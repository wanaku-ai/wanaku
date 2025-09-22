package ai.wanaku.tool.http;

import static ai.wanaku.core.runtime.camel.CamelQueryHelper.safeLog;

import ai.wanaku.api.exceptions.WanakuException;
import ai.wanaku.core.capabilities.common.ParsedToolInvokeRequest;
import ai.wanaku.core.capabilities.tool.Client;
import ai.wanaku.core.config.provider.api.ConfigResource;
import ai.wanaku.core.config.provider.api.ReservedConfigs;
import ai.wanaku.core.exchange.ToolInvokeRequest;
import ai.wanaku.core.runtime.camel.CamelQueryParameterBuilder;
import ai.wanaku.core.util.CollectionsHelper;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import java.util.HashMap;
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
    public Object exchange(ToolInvokeRequest request, ConfigResource configResource) throws WanakuException {
        producer.start();

        CamelQueryParameterBuilder parameterBuilder = new CamelQueryParameterBuilder(configResource);
        ParsedToolInvokeRequest parsedRequest =
                ParsedToolInvokeRequest.parseRequest(request.getUri(), request, parameterBuilder::build);

        Map<String, String> configsMap = configResource.getConfigs(ReservedConfigs.CONFIG_HEADER_PARAMETERS_PREFIX);
        Map<String, String> configsMapNoPrefix = new HashMap<String, String>();

        if (!configsMap.isEmpty()) {
            for (var entry : configsMap.entrySet()) {
                configsMapNoPrefix.put(
                        entry.getKey().substring(ReservedConfigs.CONFIG_HEADER_PARAMETERS_PREFIX.length()),
                        entry.getValue());
            }
        }
        
        Map<String, Object> headers = CollectionsHelper.toStringObjectMap(configsMapNoPrefix);

        if (headers.isEmpty()) {
            headers.put("CamelHttpMethod", "GET");
        }

        LOG.infof("Invoking tool at URI: %s", safeLog(parsedRequest.uri()));

        return producer.requestBodyAndHeaders(parsedRequest.uri(), parsedRequest.body(), headers, String.class);
    }
}
