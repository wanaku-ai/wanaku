package ai.wanaku.cli.main.support;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.jline.builtins.ConfigurationPath;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import ai.wanaku.capabilities.sdk.api.types.ToolReference;
import ai.wanaku.capabilities.sdk.api.types.WanakuResponse;
import ai.wanaku.cli.main.commands.BaseCommand;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledOnOs(OS.WINDOWS)
class LabelHelperTest {

    private WanakuPrinter printer;

    @BeforeEach
    void setUp() throws IOException {
        Terminal terminal = TerminalBuilder.builder().dumb(true).build();
        ConfigurationPath configPath = new ConfigurationPath((Path) null, null);
        printer = new WanakuPrinter(configPath, terminal);
    }

    @Nested
    class ParseLabelsTests {

        @Test
        void returnsEmptyMapWhenLabelsIsNull() {
            Map<String, String> result = LabelHelper.parseLabels(null, printer);

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        void returnsEmptyMapWhenLabelsIsEmpty() {
            Map<String, String> result = LabelHelper.parseLabels(List.of(), printer);

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }

        @Test
        void parsesValidLabels() {
            Map<String, String> result = LabelHelper.parseLabels(List.of("env=prod", "team=backend"), printer);

            assertEquals(2, result.size());
            assertEquals("prod", result.get("env"));
            assertEquals("backend", result.get("team"));
        }

        @Test
        void returnsNullForInvalidFormat() {
            Map<String, String> result = LabelHelper.parseLabels(List.of("no-equals-sign"), printer);

            assertNull(result);
        }

        @Test
        void trimsKeysAndValues() {
            Map<String, String> result = LabelHelper.parseLabels(List.of(" env = prod "), printer);

            assertEquals(1, result.size());
            assertEquals("prod", result.get("env"));
        }
    }

    @Nested
    class AddLabelsToEntityTests {

        @Test
        void addsNewLabels() {
            ToolReference tool = createTool("my-tool", new HashMap<>());
            AtomicReference<ToolReference> updated = new AtomicReference<>();

            int result = LabelHelper.addLabelsToEntity(
                    tool, Map.of("env", "prod"), printer, updated::set, "tool", "my-tool");

            assertEquals(BaseCommand.EXIT_OK, result);
            assertNotNull(updated.get());
            assertEquals("prod", updated.get().getLabels().get("env"));
        }

        @Test
        void updatesExistingLabel() {
            ToolReference tool = createTool("my-tool", new HashMap<>(Map.of("env", "dev")));
            AtomicReference<ToolReference> updated = new AtomicReference<>();

            int result = LabelHelper.addLabelsToEntity(
                    tool, Map.of("env", "prod"), printer, updated::set, "tool", "my-tool");

            assertEquals(BaseCommand.EXIT_OK, result);
            assertEquals("prod", updated.get().getLabels().get("env"));
        }

        @Test
        void preservesExistingLabels() {
            ToolReference tool = createTool("my-tool", new HashMap<>(Map.of("tier", "backend")));
            AtomicReference<ToolReference> updated = new AtomicReference<>();

            LabelHelper.addLabelsToEntity(tool, Map.of("env", "prod"), printer, updated::set, "tool", "my-tool");

            assertEquals("backend", updated.get().getLabels().get("tier"));
            assertEquals("prod", updated.get().getLabels().get("env"));
        }
    }

    @Nested
    class RemoveLabelsFromEntityTests {

        @Test
        void removesExistingLabel() {
            ToolReference tool = createTool("my-tool", new HashMap<>(Map.of("env", "dev", "tier", "backend")));
            AtomicReference<ToolReference> updated = new AtomicReference<>();

            int result =
                    LabelHelper.removeLabelsFromEntity(tool, List.of("env"), printer, updated::set, "tool", "my-tool");

            assertEquals(BaseCommand.EXIT_OK, result);
            assertNotNull(updated.get());
            assertEquals(1, updated.get().getLabels().size());
            assertEquals("backend", updated.get().getLabels().get("tier"));
        }

        @Test
        void skipsNonexistentLabelWithoutCallingUpdater() {
            ToolReference tool = createTool("my-tool", new HashMap<>(Map.of("env", "dev")));
            AtomicReference<ToolReference> updated = new AtomicReference<>();

            int result = LabelHelper.removeLabelsFromEntity(
                    tool, List.of("nonexistent"), printer, updated::set, "tool", "my-tool");

            assertEquals(BaseCommand.EXIT_OK, result);
            assertNull(updated.get());
        }

        @Test
        void removesMultipleLabels() {
            ToolReference tool =
                    createTool("my-tool", new HashMap<>(Map.of("env", "dev", "tier", "backend", "team", "core")));
            AtomicReference<ToolReference> updated = new AtomicReference<>();

            int result = LabelHelper.removeLabelsFromEntity(
                    tool, List.of("env", "tier"), printer, updated::set, "tool", "my-tool");

            assertEquals(BaseCommand.EXIT_OK, result);
            assertEquals(1, updated.get().getLabels().size());
            assertEquals("core", updated.get().getLabels().get("team"));
        }
    }

    @Nested
    class AddLabelsByExpressionTests {

        @Test
        void returnsOkWhenNoMatchingEntities() {
            WanakuResponse<List<ToolReference>> response = new WanakuResponse<>(List.of());

            int result = LabelHelper.addLabelsByExpression(
                    response, Map.of("env", "prod"), printer, t -> {}, ToolReference::getName, "tool(s)", "env=dev");

            assertEquals(BaseCommand.EXIT_OK, result);
        }

        @Test
        void addsLabelsToAllMatchingEntities() {
            ToolReference tool1 = createTool("tool-1", new HashMap<>());
            ToolReference tool2 = createTool("tool-2", new HashMap<>());
            WanakuResponse<List<ToolReference>> response = new WanakuResponse<>(List.of(tool1, tool2));

            int result = LabelHelper.addLabelsByExpression(
                    response,
                    Map.of("env", "prod"),
                    printer,
                    t -> {},
                    ToolReference::getName,
                    "tool(s)",
                    "tier=backend");

            assertEquals(BaseCommand.EXIT_OK, result);
            assertEquals("prod", tool1.getLabels().get("env"));
            assertEquals("prod", tool2.getLabels().get("env"));
        }

        @Test
        void returnsOkForNullData() {
            WanakuResponse<List<ToolReference>> response = new WanakuResponse<>(null);

            int result = LabelHelper.addLabelsByExpression(
                    response, Map.of("env", "prod"), printer, t -> {}, ToolReference::getName, "tool(s)", "env=dev");

            assertEquals(BaseCommand.EXIT_OK, result);
        }
    }

    @Nested
    class RemoveLabelsByExpressionTests {

        @Test
        void returnsOkWhenNoMatchingEntities() {
            WanakuResponse<List<ToolReference>> response = new WanakuResponse<>(List.of());

            int result = LabelHelper.removeLabelsByExpression(
                    response, List.of("env"), printer, t -> {}, ToolReference::getName, "tool(s)", "env=dev");

            assertEquals(BaseCommand.EXIT_OK, result);
        }

        @Test
        void removesLabelsFromMatchingEntities() {
            ToolReference tool1 = createTool("tool-1", new HashMap<>(Map.of("env", "dev", "tier", "backend")));
            ToolReference tool2 = createTool("tool-2", new HashMap<>(Map.of("env", "prod")));
            WanakuResponse<List<ToolReference>> response = new WanakuResponse<>(List.of(tool1, tool2));

            int result = LabelHelper.removeLabelsByExpression(
                    response, List.of("env"), printer, t -> {}, ToolReference::getName, "tool(s)", "tier=backend");

            assertEquals(BaseCommand.EXIT_OK, result);
            assertTrue(tool1.getLabels().containsKey("tier"));
            assertEquals(1, tool1.getLabels().size());
            assertTrue(tool2.getLabels().isEmpty());
        }

        @Test
        void skipsUnmodifiedEntities() {
            ToolReference tool = createTool("tool-1", new HashMap<>(Map.of("env", "dev")));
            WanakuResponse<List<ToolReference>> response = new WanakuResponse<>(List.of(tool));
            AtomicReference<ToolReference> updated = new AtomicReference<>();

            LabelHelper.removeLabelsByExpression(
                    response,
                    List.of("nonexistent"),
                    printer,
                    updated::set,
                    ToolReference::getName,
                    "tool(s)",
                    "env=dev");

            assertNull(updated.get());
        }
    }

    private ToolReference createTool(String name, Map<String, String> labels) {
        ToolReference tool = new ToolReference();
        tool.setName(name);
        if (labels != null) {
            tool.setLabels(labels);
        }
        return tool;
    }
}
