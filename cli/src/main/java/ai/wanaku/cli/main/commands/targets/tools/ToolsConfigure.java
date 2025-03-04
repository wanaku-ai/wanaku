package ai.wanaku.cli.main.commands.targets.tools;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import ai.wanaku.cli.main.commands.targets.AbstractTargetsConfigure;
import picocli.CommandLine;

@CommandLine.Command(name = "configure",
        description = "Configure services")
public class ToolsConfigure extends AbstractTargetsConfigure {

    @Override
    public void run() {
        initService();

        try {
            linkService.toolsConfigure(service, option, value);
        } catch (WebApplicationException e) {
            Response response = e.getResponse();

            if (response.getStatus() == Response.Status.NOT_FOUND.getStatusCode()) {
                System.out.println("There is no configuration or service with that name");
            }
        }

    }
}
