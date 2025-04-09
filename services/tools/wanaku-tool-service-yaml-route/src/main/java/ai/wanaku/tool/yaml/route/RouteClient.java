package ai.wanaku.tool.yaml.route;

import jakarta.enterprise.context.ApplicationScoped;

import ai.wanaku.api.exceptions.WanakuException;
import ai.wanaku.core.exchange.ParsedToolInvokeRequest;
import ai.wanaku.core.exchange.ToolInvokeRequest;
import ai.wanaku.core.services.config.WanakuToolConfig;
import ai.wanaku.core.services.tool.Client;
import jakarta.inject.Inject;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.spi.Resource;
import org.apache.camel.support.PluginHelper;
import org.jboss.logging.Logger;

@ApplicationScoped
public class RouteClient implements Client {
    private static final Logger LOG = Logger.getLogger(RouteClient.class);

    @Inject
    CamelContext camelContext;
    @Inject
    ProducerTemplate producer;
    private final WanakuToolConfig config;

    public RouteClient(WanakuToolConfig config) {
        this.config = config;
    }

    @Override
    public Object exchange(ToolInvokeRequest request) throws WanakuException {

        LOG.infof("Loading resource from URI: %s", request.getUri());
        Resource resource = PluginHelper.getResourceLoader(camelContext).resolveResource(request.getUri());
        try {
            PluginHelper.getRoutesLoader(camelContext).loadRoutes(resource);
        } catch (Exception e) {
            throw new WanakuException(e);
        }

        ParsedToolInvokeRequest parsedRequest = ParsedToolInvokeRequest.parseRequest(request);

        LOG.infof("Invoking tool at URI: %s", parsedRequest.uri());
        return producer.requestBody(config.baseUri(), parsedRequest.body(), String.class);
    }
}
