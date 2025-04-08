package ai.wanaku.tool.telegram;

import java.util.Map;

import jakarta.enterprise.context.ApplicationScoped;

import ai.wanaku.core.exchange.ParsedToolInvokeRequest;
import ai.wanaku.core.exchange.ToolInvokeRequest;
import ai.wanaku.core.services.tool.Client;
import jakarta.inject.Inject;
import org.apache.camel.ProducerTemplate;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
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
            return String.format("Unexcpected error occured while sending the messege ");
        }

        return String.format("Message sent successfully to telegramId %s", chatId);
    }
}