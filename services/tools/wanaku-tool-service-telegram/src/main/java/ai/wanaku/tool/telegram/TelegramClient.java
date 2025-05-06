package ai.wanaku.tool.telegram;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import ai.wanaku.core.exchange.ToolInvokeRequest;
import ai.wanaku.core.services.tool.Client;
import java.util.Map;
import org.apache.camel.ProducerTemplate;
import org.jboss.logging.Logger;

@ApplicationScoped
public class TelegramClient implements Client {
    private static final Logger LOG = Logger.getLogger(TelegramClient.class);

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
        try{
            producer.sendBody(baseUri, message);

        } catch(Exception e){
            e.printStackTrace();
            return String.format("Unexpected error occurred while sending the message ");
        }

        return String.format("Message sent successfully to telegramId %s", chatId);
    }
}