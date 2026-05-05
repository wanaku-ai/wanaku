package ai.wanaku.cli.main.commands.tools;

import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.jline.terminal.Terminal;
import ai.wanaku.capabilities.sdk.api.types.ToolReference;
import ai.wanaku.capabilities.sdk.api.types.WanakuResponse;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.CommandHelper;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.core.services.api.ToolsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import picocli.CommandLine;

import static io.netty.handler.codec.http.HttpResponseStatus.OK;

@CommandLine.Command(name = "edit", description = "edit tool")
public class ToolsEdit extends BaseCommand {

    @CommandLine.Option(
            names = {"--host"},
            description = "The API host",
            defaultValue = "http://localhost:8080",
            arity = "0..1")
    protected String host;

    @CommandLine.Parameters(description = "Tool name that you want to edit", arity = "0..1")
    private String toolName;

    ToolsService toolsService;

    private static final String TEMP_FILE_PREFIX = "wanaku_edit";
    private static final String TEMP_FILE_SUFFIX = ".json";

    ObjectMapper mapper = new ObjectMapper();

    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws IOException, Exception {
        toolsService = initService(ToolsService.class, host);

        List<ToolReference> list = toolsService.list().data();

        if (list == null || list.isEmpty()) {
            System.out.println("No tools registered yet");
            return EXIT_ERROR;
        }

        ToolReference tool;
        if (toolName == null) {
            tool = selectTool(terminal, list);
        } else {
            try {
                WanakuResponse<ToolReference> response = toolsService.getByName(toolName);
                tool = response.data();
            } catch (RuntimeException e) {
                System.err.println("Error retrieving the tool '" + toolName + "': " + e.getMessage());
                return EXIT_ERROR;
            }
        }

        String toolString = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(tool);
        String modifiedContent = edit(toolString, tool.getName());
        boolean wasModified = !toolString.equals(modifiedContent);
        if (!wasModified) {
            printer.printWarningMessage("No changes detected!");
            return EXIT_OK;
        }

        boolean save = CommandHelper.confirm(terminal, "Do you want to update the '" + tool.getName() + "' tool?");

        if (save) {
            System.out.print("saving '" + tool.getName() + "' tool...");
            ToolReference toolModified = mapper.readValue(modifiedContent, ToolReference.class);
            toolModified.setId(tool.getId());
            Response response = toolsService.update(tool.getName(), toolModified);
            if (response.getStatus() != OK.code()) {
                System.err.println("error!");
            }
            System.out.println("done");
        }
        return EXIT_OK;
    }

    public String edit(String toolString, String toolName) throws IOException, InterruptedException {
        Path tempFile = Files.createTempFile(TEMP_FILE_PREFIX, TEMP_FILE_SUFFIX);
        Files.write(tempFile, toolString.getBytes());

        String editor = System.getenv("EDITOR");
        if (editor == null || editor.isBlank()) {
            editor = System.getenv("VISUAL");
        }
        if (editor == null || editor.isBlank()) {
            editor = "nano";
        }

        ProcessBuilder pb = new ProcessBuilder(editor, tempFile.toAbsolutePath().toString());
        pb.inheritIO();
        Process process = pb.start();
        int exitCode = process.waitFor();

        if (exitCode != 0) {
            throw new IOException("Editor exited with code " + exitCode);
        }

        return Files.readString(tempFile);
    }

    public ToolReference selectTool(Terminal terminal, List<ToolReference> list) throws IOException {
        List<String> items = list.stream()
                .map(tool -> {
                    String desc = tool.getDescription();
                    if (desc.length() > 60) {
                        desc = desc.substring(0, 57) + "...";
                    }
                    return String.format("%-30s %s", tool.getName(), desc);
                })
                .toList();

        int index = CommandHelper.selectFromList(terminal, "Choose the tool you want to edit:", items);
        return list.get(index);
    }
}
