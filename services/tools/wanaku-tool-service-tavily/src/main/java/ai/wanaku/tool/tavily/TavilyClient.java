package ai.wanaku.tool.tavily;

import ai.wanaku.core.exchange.ParsedToolInvokeRequest;
import ai.wanaku.core.exchange.ToolInvokeRequest;
import ai.wanaku.core.services.tool.Client;
import dev.langchain4j.web.search.WebSearchEngine;
import dev.langchain4j.web.search.tavily.TavilyWebSearchEngine;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

@ApplicationScoped
public class TavilyClient implements Client {
    private static final Logger LOG = Logger.getLogger(TavilyClient.class);

    @Inject
    ProducerTemplate producer;

    @Inject
    CamelContext camelContext;

    WebSearchEngine tavilyWebSearchEngine;

    public TavilyClient() {

        String tavilyApiKey = ConfigProvider.getConfig().getConfigValue("tavily.api.key").getValue();

        tavilyWebSearchEngine = TavilyWebSearchEngine.builder()
                .apiKey(tavilyApiKey)
                .includeRawContent(false)
                .build();

         camelContext.getRegistry().bind("tavily", tavilyWebSearchEngine);
    }

    @Override
    public Object exchange(ToolInvokeRequest request) {
        String baseUri = "langchain4j-web-search:test?webSearchEngine=#tavily&resultType=SNIPPET";
        if (request.getArgumentsMap().containsKey("maxResults")) {
            baseUri += "&maxResults={maxResults}";
        }

        ParsedToolInvokeRequest parsedRequest = ParsedToolInvokeRequest.parseRequest(baseUri, request);
        return producer.requestBody(parsedRequest.uri(), parsedRequest.body());

    }
}