package ai.wanaku.tool.tavily;

import ai.wanaku.core.capabilities.config.WanakuServiceConfig;
import ai.wanaku.core.config.provider.api.ConfigResource;
import ai.wanaku.core.capabilities.common.ParsedToolInvokeRequest;
import ai.wanaku.core.exchange.ToolInvokeRequest;
import ai.wanaku.core.capabilities.tool.Client;
import ai.wanaku.core.runtime.camel.CamelQueryParameterBuilder;
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

    private final ProducerTemplate producer;
    private final CamelContext camelContext;

    @Inject
    WanakuServiceConfig config;

    public TavilyClient(CamelContext camelContext) {
        this.camelContext = camelContext;
        this.producer = camelContext.createProducerTemplate();
    }

    @Override
    public Object exchange(ToolInvokeRequest request, ConfigResource configResource) {
        try {
            String tavilyApiKey = configResource.getSecret("tavily.api.key");

            WebSearchEngine tavilyWebSearchEngine = TavilyWebSearchEngine.builder()
                    .apiKey(tavilyApiKey)
                    .includeRawContent(false)
                    .build();

            camelContext.getRegistry().bind("tavily", tavilyWebSearchEngine);

            producer.start();

            String baseUri = config.baseUri();
            CamelQueryParameterBuilder parameterBuilder = new CamelQueryParameterBuilder(configResource);
            ParsedToolInvokeRequest parsedRequest = ParsedToolInvokeRequest.parseRequest(baseUri, request, parameterBuilder::build);

            return producer.requestBody(parsedRequest.uri(), parsedRequest.body());
        } finally {
            producer.stop();
        }
    }
}