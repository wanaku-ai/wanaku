package ai.wanaku.tool.yaml.route;

import static ai.wanaku.core.runtime.camel.CamelQueryHelper.safeLog;

import ai.wanaku.api.exceptions.WanakuException;
import ai.wanaku.core.capabilities.common.ParsedToolInvokeRequest;
import ai.wanaku.core.capabilities.config.WanakuServiceConfig;
import ai.wanaku.core.capabilities.tool.Client;
import ai.wanaku.core.config.provider.api.ConfigResource;
import ai.wanaku.core.exchange.ToolInvokeRequest;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.spi.Resource;
import org.apache.camel.support.PluginHelper;
import org.jboss.logging.Logger;

@ApplicationScoped
public class RouteClient implements Client {
    private static final Logger LOG = Logger.getLogger(RouteClient.class);

    private final CamelContext camelContext;
    private final ProducerTemplate producer;
    private final WanakuServiceConfig config;

    public RouteClient(CamelContext camelContext, WanakuServiceConfig config) {
        this.camelContext = camelContext;
        this.producer = camelContext.createProducerTemplate();
        this.config = config;
    }

    @Override
    public Object exchange(ToolInvokeRequest request, ConfigResource configResource) throws WanakuException {
        producer.start();

        LOG.infof("Loading resource from URI: %s", request.getUri());
        Resource resource = PluginHelper.getResourceLoader(camelContext).resolveResource(request.getUri());
        try {
            PluginHelper.getRoutesLoader(camelContext).loadRoutes(resource);
        } catch (Exception e) {
            throw new WanakuException(e);
        }

        ParsedToolInvokeRequest parsedRequest = ParsedToolInvokeRequest.parseRequest(request, configResource);

        LOG.infof("Invoking tool at URI: %s", safeLog(parsedRequest.uri()));
        return producer.requestBody(config.baseUri(), parsedRequest.body(), String.class);
    }
}
