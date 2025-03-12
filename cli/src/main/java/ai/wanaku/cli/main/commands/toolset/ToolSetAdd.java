package ai.wanaku.cli.main.commands.toolset;

import ai.wanaku.api.types.ToolReference;
import ai.wanaku.cli.main.commands.BaseCommand;
import ai.wanaku.cli.main.support.PropertyHelper;
import ai.wanaku.core.util.IndexHelper;
import org.jboss.logging.Logger;
import picocli.CommandLine;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@CommandLine.Command(name = "add",description = "Add tools to a toolset")
public class ToolSetAdd extends BaseCommand {
    private static final Logger LOG = Logger.getLogger(ToolSetAdd.class);

    @CommandLine.Parameters(description="Path to the toolset", arity = "1..1")
    private String path;

    @CommandLine.Option(names = {"-n", "--name"}, description="Name of the tool reference", required = true)
    private String name;

    @CommandLine.Option(names = {"-d", "--description"}, description="Description of the tool reference", required = true)
    private String description;

    @CommandLine.Option(names = {"-u", "--uri"}, description="URI of the tool", required = true)
    private String uri;

    @CommandLine.Option(names = {"--type"}, description="Type of the tool reference (i.e: http)", required = true)
    private String type;

    @CommandLine.Option(names = {"--input-schema-type"},
            description = "Type of input schema (e.g., 'string', 'object')",
            defaultValue = "object")
    private String inputSchemaType;

    @CommandLine.Option(names = {"-p", "--property"},
            description = "Property name and value (e.g., '--property name:type,description)")
    private List<String> properties;

    @CommandLine.Option(names = {"-r", "--required"},
            description = "List of required property names (e.g., '-r foo bar')", arity = "0..*")
    private List<String> required;

    @Override
    public void run() {
        ToolReference toolReference = new ToolReference();

        toolReference.setName(name);
        toolReference.setDescription(description);
        toolReference.setUri(uri);
        toolReference.setType(type);

        ToolReference.InputSchema inputSchema = new ToolReference.InputSchema();
        inputSchema.setType(inputSchemaType);

        if (properties != null) {
            for (String propertyStr : properties) {
                PropertyHelper.PropertyDescription result = PropertyHelper.parseProperty(propertyStr);

                ToolReference.Property property = new ToolReference.Property();
                property.setType(result.dataType());
                property.setDescription(result.description());
                property.setPropertyName(result.propertyName());

                inputSchema.getProperties().add(property);
            }
        }

        toolReference.setInputSchema(inputSchema);

        if (required != null) {
            inputSchema.setRequired(required);
        }


        try {
            List<ToolReference> toolReferences;
            File indexFile = new File(path);
            if (indexFile.exists()) {
                toolReferences = IndexHelper.loadToolsIndex(indexFile);
            } else {
                indexFile.getParentFile().mkdirs();
                toolReferences = new ArrayList<>();
            }

            toolReferences.add(toolReference);
            IndexHelper.saveToolsIndex(indexFile, toolReferences);
        } catch (Exception e) {
            LOG.errorf(e, "Failed to load tools index: %s", e.getMessage());
            throw new RuntimeException(e);
        }

    }

}
