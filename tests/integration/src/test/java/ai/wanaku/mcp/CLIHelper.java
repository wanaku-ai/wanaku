package ai.wanaku.mcp;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

public class CLIHelper {
    private static final Logger LOG = LoggerFactory.getLogger(CLIHelper.class);

    private CLIHelper() {}

    /**
     * Executes a command using the Wanaku CLI.
     *
     * @param command The command to execute, as a list of strings.
     * @param host The host running the Wanaku MCP Router
     * @return The exit code of the command.
     */
    public static int executeWanakuCliCommand(List<String> command, String host) {
        List<String> executableCommand = new ArrayList<>(command);
        if ("wanaku".equals(executableCommand.get(0))) {
            executableCommand.remove(0);
        }

        if (host != null) {
            executableCommand.add(String.format("--host=%s", host));
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        PrintWriter printOut = new PrintWriter(out);
        PrintWriter printErr = new PrintWriter(err);

        CommandLine cmd = new CommandLine(WanakuIntegrationBase.cliMain)
                .setOut(printOut)
                .setErr(printErr);

        LOG.debug("Executing command via wanaku CLI: {}", executableCommand);

        int result = cmd.execute(executableCommand.toArray(new String[0]));

        LOG.info("Wanaku command out: {}", out);
        LOG.error("Wanaku command err: {}", err);

        Assertions.assertThat(result)
                .as("The command: " + executableCommand + " didn't run successfully")
                .isEqualTo(0);

        return result;
    }
}
