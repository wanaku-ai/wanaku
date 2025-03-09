package ai.wanaku.routing.service;

import java.util.List;

import ai.wanaku.core.exchange.ParsedToolInvokeRequest;
import ai.wanaku.core.exchange.ToolInvokeRequest;
import ai.wanaku.core.services.routing.Client;
import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.tavily.TavilyWebSearchEngine;
import jakarta.enterprise.context.ApplicationScoped;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

@ApplicationScoped
public class TavilyClient implements Client {
    private static final Logger LOG = Logger.getLogger(TavilyClient.class);

    private final ProducerTemplate producer;

    @ConfigProperty(name = "tavily.api.key")
    String tavilyApiKey;

    WebSearchEngine tavilyWebSearchEngine;

    public TavilyClient(CamelContext camelContext) {
        this.producer = camelContext.createProducerTemplate();


         tavilyWebSearchEngine = TavilyWebSearchEngine.builder()
                .apiKey(tavilyApiKey)
                .includeRawContent(true)
                .build();

         camelContext.getRegistry().bind("tavily", tavilyWebSearchEngine);
    }

    @Override
    public Object exchange(ToolInvokeRequest request) {
        try {
            producer.start();

            ParsedToolInvokeRequest parsedRequest = ParsedToolInvokeRequest.parseRequest(request);

            return producer.requestBody("langchain4j-web-search:test?webSearchEngine=#tavily",
                    parsedRequest.body(), List.class);
        } finally {
            producer.stop();
        }
    }
}