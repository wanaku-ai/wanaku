package ai.wanaku.cli.main.commands.tools;

import ai.wanaku.api.types.ToolReference;
import ai.wanaku.api.types.WanakuResponse;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.WanakuPrinter;
import ai.wanaku.core.services.api.ToolsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.core.Response;
import org.jline.builtins.ConfigurationPath;
import org.jline.builtins.Nano;
import org.jline.builtins.Options;
import org.jline.consoleui.elements.ConfirmChoice;
import org.jline.consoleui.prompt.ConsolePrompt;
import org.jline.consoleui.prompt.PromptResultItemIF;
import org.jline.consoleui.prompt.builder.ListPromptBuilder;
import org.jline.consoleui.prompt.builder.PromptBuilder;
import org.jline.terminal.Terminal;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static io.netty.handler.codec.http.HttpResponseStatus.OK;

@CommandLine.Command(name = "edit", description = "edit tool")

public class ToolsEdit extends BaseCommand {

    @CommandLine.Option(names = {"--host"}, description = "The API host", defaultValue = "http://localhost:8080", arity = "0..1")
    protected String host;

    @CommandLine.Parameters(description = "Tool name that you want to edit", arity = "0..1")
    private String toolName;

    ToolsService toolsService;

    private static final String TEMP_FILE_PREFIX = "wanaku_edit";

    private static final String TEMP_FILE_SUFFIX = ".json";

    private static final String NANO_CONFIG_FILE = "/nano/jnanorc";

    private record Item(String id, String text) {
    }

    ObjectMapper mapper = new ObjectMapper();


    /**
     * This method is the entry point for the `edit` command.
     * It allows users to edit a tool's definition either by providing the tool name as a parameter
     * or by selecting from a list of registered tools.
     * It uses the Nano editor for modifying the tool definition and prompts for confirmation before saving.
     *
     * @return EXIT_OK; if the operation was successful, 1 otherwise.
     * @throws Exception if an error occurs during the operation.
     */
    @Override
    public Integer doCall(Terminal terminal, WanakuPrinter printer) throws IOException, Exception {
        toolsService = initService(ToolsService.class, host);

        List<ToolReference> list = toolsService.list().data();

        // check if there is at least a tool registered
        if (list == null || list.isEmpty()) {
            System.out.println("No tools registered yet");
            return EXIT_ERROR;
        }

        ToolReference tool;
        if (toolName == null) {
            // if no tool name provided as parameter, let the user choose from the list
            // of registered tools.
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

        //Run Nano Editor to modify the tool
        String toolString = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(tool);
        String modifiedContent = edit(terminal, toolString, tool.getName());
        boolean wasModified = !toolString.equals(modifiedContent);
        if (!wasModified) {
            printer.printWarningMessage("No changes detected!");
            return EXIT_OK;
        }
        //Ask for confirmation
        boolean save = confirm(terminal, tool);

        if (save) {
            System.out.print("saving '" + tool.getName() + "' tool...");
            ToolReference toolModified = mapper.readValue(modifiedContent, ToolReference.class);
            // Force the ID in case the user modified it.
            toolModified.setId(tool.getId());
            Response response = toolsService.update(toolModified);
            if (response.getStatus() != OK.code()) {
                System.err.println("error!");
            }
            System.out.println("done");
        }
        return EXIT_OK;
    }

    /**
     * Prompts the user for confirmation to update the specified tool.
     *
     * @param terminal The JLine terminal instance.
     * @param tool     The ToolReference object to be updated.
     * @return true if the user confirms the update, false otherwise.
     * @throws IOException if an I/O error occurs during the prompt.
     */
    public boolean confirm(Terminal terminal, ToolReference tool) throws IOException {
        ConsolePrompt prompt = new ConsolePrompt(terminal);
        PromptBuilder builder = prompt.getPromptBuilder();
        // Create a simple yes/no prompt

        builder.createConfirmPromp()
                .name("continue")
                .message("Do you want to update the '" + tool.getName() + "' tool?")
                .defaultValue(ConfirmChoice.ConfirmationValue.NO)
                .addPrompt();

        Map<String, PromptResultItemIF> result = prompt.prompt(builder.build());
        return "YES".equalsIgnoreCase(result.get("continue").getResult());
    }


    /**
     * Runs the Nano editor to allow the user to modify the selected tool definition.
     * The tool definition is written to a temporary file, edited using Nano, and then the modified content is read back.
     *
     * @param terminal   The JLine terminal instance.
     * @param toolString The initial JSON string representation of the tool.
     * @param toolName   The name of the tool being edited.
     * @return The modified content of the tool definition as a String.
     * @throws IOException if an I/O error occurs during file operations or running Nano.
     */
    public String edit(Terminal terminal, String toolString, String toolName) throws IOException {
        Path tempFile = Files.createTempFile(TEMP_FILE_PREFIX, TEMP_FILE_SUFFIX);
        ConfigurationPath configPath = ConfigurationPath.fromClasspath(NANO_CONFIG_FILE);
        Path root = tempFile.getParent();
        Files.write(tempFile, toolString.getBytes());
        Options options = Options.compile(Nano.usage()).parse(new String[0]);
        Nano nano = new Nano(terminal, root, options, configPath);
        nano.title = "Edit Tool " + toolName;
        nano.mouseSupport = true;
        nano.matchBrackets = "(<[{)>]}";
        nano.printLineNumbers = true;
        nano.open(tempFile.toAbsolutePath().toString());
        nano.run();

        return Files.readString(tempFile);
    }

    /**
     * Presents a list of available tools to the user and allows them to select one.
     * The list is formatted for better readability in the terminal.
     *
     * @param terminal The JLine terminal instance.
     * @param list     A list of ToolReference objects to choose from.
     * @return The selected ToolReference object.
     * @throws IOException if an I/O error occurs during the prompt.
     */
    public ToolReference selectTool(Terminal terminal, List<ToolReference> list) throws IOException {

        ConsolePrompt.UiConfig uiConfig = new ConsolePrompt.UiConfig("=> ", "[]", "[x]", "-");
        ConsolePrompt prompt = new ConsolePrompt(terminal, uiConfig);

        PromptBuilder builder = prompt.getPromptBuilder();

        ListPromptBuilder lpb = builder.createListPrompt().name("tool")
                .message("Choose the tool you want to edit:").pageSize(5);

        List<Item> items = formatTable(list);

        items.stream().forEach(
                x -> lpb
                        .newItem()
                        .text(x.text)
                        .name(x.id)
                        .add()
        );
        lpb.addPrompt();
        Map<String, PromptResultItemIF> result = prompt.prompt(builder.build());
        String toolId = result.get("tool").getResult();
        ToolReference tool = list.stream().filter(x -> x.getId().equals(toolId)).findFirst().orElse(null);
        return tool;
    }


    /**
     * Formats a list of ToolReference objects into a human-readable table format
     * suitable for display in the terminal. It truncates long descriptions and aligns columns.
     *
     * @param list The list of ToolReference objects to format.
     * @return A list of `Item` records, where each `Item` contains the tool's ID and its formatted text representation.
     */
    private List<Item> formatTable(List<ToolReference> list) {
        final int MAX_DESCRIPTION_DISPLAY_LENGTH = 60;
        final int COLUMN_PADDING = 3;
        final String TRUNCATE_INDICATOR = "...";

        int maxNameLength = list.stream()
                .mapToInt(item -> item.getName().length())
                .max()
                .orElse(0);
        int maxDescriptionDisplayLength = list.stream()
                .mapToInt(item -> Math.min(item.getDescription().length(), MAX_DESCRIPTION_DISPLAY_LENGTH))
                .max()
                .orElse(0);

        final int nameColumnWidth = Math.max(maxNameLength + COLUMN_PADDING, "Name".length() + COLUMN_PADDING);
        final int descriptionColumnWidth = Math.max(maxDescriptionDisplayLength + COLUMN_PADDING, "Description".length() + COLUMN_PADDING);

        return list.stream().map(
                item -> {
                    String name = item.getName();
                    String description = item.getDescription();
                    if (description.length() > MAX_DESCRIPTION_DISPLAY_LENGTH) {
                        // Adjust for the length of the truncation indicator
                        int truncateTo = MAX_DESCRIPTION_DISPLAY_LENGTH - TRUNCATE_INDICATOR.length();
                        description = description.substring(0, truncateTo) + TRUNCATE_INDICATOR;
                    }
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    PrintStream ps = new PrintStream(baos);
                    ps.printf("%-" + nameColumnWidth + "s %-" + descriptionColumnWidth + "s%n", name, description);
                    return new Item(item.getId(), baos.toString());
                }
        ).toList();
    }
}
