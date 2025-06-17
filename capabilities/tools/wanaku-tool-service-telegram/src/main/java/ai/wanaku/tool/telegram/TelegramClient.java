package ai.wanaku.tool.telegram;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import ai.wanaku.core.exchange.ToolInvokeRequest;
import ai.wanaku.core.capabilities.tool.Client;
import java.util.Map;
import org.apache.camel.ProducerTemplate;

@ApplicationScoped
public class TelegramClient implements Client {
    @Inject
    ProducerTemplate producer;

    @Override
    public Object exchange(ToolInvokeRequest request) {

        Map<String, String> serviceConfigurationsMap = request.getServiceConfigurationsMap();

        String authToken = serviceConfigurationsMap.get("authorizationToken");

        String chatId =  serviceConfigurationsMap.get("telegramId");

        if(chatId == null) {
            chatId = request.getArgumentsMap().get("telegramId");
        }

        String baseUri = String.format("telegram:bots?authorizationToken=%s&chatId=%s", authToken, chatId);

        String message = request.getArgumentsMap().get("message");
        producer.sendBody(baseUri, message);
        return String.format("Message sent successfully to telegramId %s", chatId);
    }
}