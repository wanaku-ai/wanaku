package ai.wanaku.tool.exec;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import org.jboss.logging.Logger;
import ai.wanaku.capabilities.sdk.config.provider.api.ConfigResource;
import ai.wanaku.core.capabilities.common.ParsedToolInvokeRequest;
import ai.wanaku.core.capabilities.tool.Client;
import ai.wanaku.core.exchange.v1.ToolInvokeRequest;

@ApplicationScoped
public class ExecClient implements Client {
    private static final Logger LOG = Logger.getLogger(ExecClient.class);

    private final ExecCommandPolicy commandPolicy;
    private final CommandExecutor commandExecutor;

    @Inject
    public ExecClient(ExecCommandPolicy commandPolicy, CommandExecutor commandExecutor) {
        this.commandPolicy = commandPolicy;
        this.commandExecutor = commandExecutor;
    }

    @Override
    public Object exchange(ToolInvokeRequest request, ConfigResource configResource) {
        ParsedToolInvokeRequest parsedRequest = ParsedToolInvokeRequest.parseRequest(request, configResource);

        List<String> command = commandPolicy.buildCommand(parsedRequest.uri());

        // Log at DEBUG level to avoid exposing sensitive data (API keys, tokens, credentials)
        // that may be embedded in the URI as query parameters
        LOG.debugf("Invoking allowlisted tool command: %s", command.get(0));

        return commandExecutor.run(command.toArray(String[]::new));
    }
}
