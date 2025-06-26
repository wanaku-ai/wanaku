package ai.wanaku.tool.telegram;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import ai.wanaku.core.capabilities.common.ParsedToolInvokeRequest;
import ai.wanaku.core.capabilities.config.WanakuServiceConfig;
import ai.wanaku.core.config.provider.api.ConfigResource;
import ai.wanaku.core.exchange.ToolInvokeRequest;
import ai.wanaku.core.capabilities.tool.Client;
import ai.wanaku.core.runtime.camel.CamelQueryParameterBuilder;
import org.apache.camel.ProducerTemplate;

@ApplicationScoped
public class TelegramClient implements Client {
    @Inject
    ProducerTemplate producer;

    @Inject
    WanakuServiceConfig config;

    @Override
    public Object exchange(ToolInvokeRequest request, ConfigResource configResource) {

        CamelQueryParameterBuilder parameterBuilder = new CamelQueryParameterBuilder(configResource);
        ParsedToolInvokeRequest parsedRequest =
                ParsedToolInvokeRequest.parseRequest(config.baseUri(), request, parameterBuilder::build);

        String message = request.getArgumentsMap().get("message");
        producer.sendBody(parsedRequest.uri(), message);
        return "Message sent successfully to telegram";
    }
}