package ${package};

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import ai.wanaku.core.config.provider.api.ConfigResource;
import ai.wanaku.core.exchange.ToolInvokeRequest;
import ai.wanaku.core.capabilities.common.ParsedToolInvokeRequest;
import ai.wanaku.core.capabilities.config.WanakuServiceConfig;
import ai.wanaku.core.capabilities.tool.Client;

#if ( $wanaku-capability-type == "camel")
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import ai.wanaku.core.runtime.camel.CamelQueryParameterBuilder;
#end
import org.jboss.logging.Logger;

#if ( $wanaku-capability-type == "camel")
import static ai.wanaku.core.runtime.camel.CamelQueryHelper.safeLog;
#end

@ApplicationScoped
public class ${name}Client implements Client {
    private static final Logger LOG = Logger.getLogger(${name}Client.class);

    @Inject
    WanakuServiceConfig config;

#if ( $wanaku-capability-type == "camel")
    private final ProducerTemplate producer;

    public ${name}Client(CamelContext camelContext) {
        this.producer = camelContext.createProducerTemplate();

        // Also create the consumer here, if needed
    }

    @Override
    public Object exchange(ToolInvokeRequest request, ConfigResource configResource) {
        producer.start();

        ParsedToolInvokeRequest parsedRequest = ParsedToolInvokeRequest.parseRequest(request, configResource);

        LOG.infof("Invoking tool at URI: %s", safeLog(parsedRequest.uri()));

        String s;
        if (parsedRequest.body().isEmpty()) {
            s = producer.requestBody(parsedRequest.uri(), null, String.class);
        } else {
            s = producer.requestBody(parsedRequest.uri(), parsedRequest.body(), String.class);
        }
        return s;
    }
#else
    public ${name}Client() {

    }

    @Override
    public Object exchange(ToolInvokeRequest request, ConfigResource configResource) {
        ParsedToolInvokeRequest parsedRequest = ParsedToolInvokeRequest.parseRequest(request, configResource);

        LOG.infof("Invoking tool at URI: %s", parsedRequest.uri());

        // Execute and return the result
        return null;
    }
#end
}