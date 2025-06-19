package ai.wanaku.tool.exec;

import ai.wanaku.core.config.provider.api.ConfigResource;
import ai.wanaku.core.util.ProcessRunner;
import java.io.File;

import jakarta.enterprise.context.ApplicationScoped;

import ai.wanaku.core.capabilities.common.ParsedToolInvokeRequest;
import ai.wanaku.core.exchange.ToolInvokeRequest;
import ai.wanaku.core.capabilities.tool.Client;
import org.jboss.logging.Logger;

@ApplicationScoped
public class ExecClient implements Client {
    private static final Logger LOG = Logger.getLogger(ExecClient.class);

    public ExecClient() {
    }

    @Override
    public Object exchange(ToolInvokeRequest request, ConfigResource configResource) {
        ParsedToolInvokeRequest parsedRequest = ParsedToolInvokeRequest.parseRequest(request, configResource);

        LOG.infof("Invoking tool at URI: %s", parsedRequest.uri());
        File fullCommand = new File(parsedRequest.uri());


        String[] arguments = fullCommand.getAbsolutePath().split(" ");
        return ProcessRunner.runWithOutput(arguments);
    }

}