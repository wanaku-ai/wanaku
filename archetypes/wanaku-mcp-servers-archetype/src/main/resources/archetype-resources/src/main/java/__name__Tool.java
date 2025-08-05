package ${package};

import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import ai.wanaku.core.forward.discovery.client.AutoDiscoveryClient;
import ai.wanaku.core.forward.discovery.client.ForwardRegistrationManager;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import io.quarkus.logging.Log;
import io.quarkus.qute.Qute;


@ApplicationScoped
public class ${name}Tool {
    @Inject
    Instance<ForwardRegistrationManager> registrationManager;

    @Tool(description = "Please write the description here")
    String toolImplementation(@ToolArg(description = "Any arguments for the tool") String arguments) {

        //Here you can implement your tool
        return "";

    }
}