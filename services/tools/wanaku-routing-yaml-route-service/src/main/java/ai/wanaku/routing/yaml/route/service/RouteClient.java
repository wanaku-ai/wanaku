package ai.wanaku.routing.yaml.route.service;

import jakarta.enterprise.context.ApplicationScoped;

import ai.wanaku.api.exceptions.WanakuException;
import ai.wanaku.core.exchange.ParsedToolInvokeRequest;
import ai.wanaku.core.exchange.ToolInvokeRequest;
import ai.wanaku.core.services.config.WanakuRoutingConfig;
import ai.wanaku.core.services.routing.Client;
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
    private final WanakuRoutingConfig config;

    public RouteClient(CamelContext camelContext, WanakuRoutingConfig config) {
        this.camelContext = camelContext;
        this.producer = camelContext.createProducerTemplate();
        this.config = config;
    }

    @Override
    public Object exchange(ToolInvokeRequest request) throws WanakuException {
        producer.start();

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
