package $

import ai.wanaku.core.forward.discovery.client.ForwardRegistrationManager;
import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;


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