package ${package};

import jakarta.enterprise.context.ApplicationScoped;

import ai.wanaku.core.exchange.ParsedToolInvokeRequest;
import ai.wanaku.core.exchange.ToolInvokeRequest;
import ai.wanaku.core.capabilities.tool.Client;

#if ( $wanaku-capability-type == "camel")
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
#end
import org.jboss.logging.Logger;

@ApplicationScoped
public class ${name}Client implements Client {
    private static final Logger LOG = Logger.getLogger(${name}Client.class);

#if ( $wanaku-capability-type == "camel")
    private final ProducerTemplate producer;

    public ${name}Client(CamelContext camelContext) {
        this.producer = camelContext.createProducerTemplate();

        // Also create the consumer here, if needed
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
#else
    public ${name}Client() {

    }

    @Override
    public Object exchange(ToolInvokeRequest request) {
        ParsedToolInvokeRequest parsedRequest = ParsedToolInvokeRequest.parseRequest(request);

        LOG.infof("Invoking tool at URI: %s", parsedRequest.uri());

        // Execute and return the result
        return null;
    }
#end
}