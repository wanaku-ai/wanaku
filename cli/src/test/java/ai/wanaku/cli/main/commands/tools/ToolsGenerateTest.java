package ai.wanaku.cli.main.commands.tools;

import ai.wanaku.cli.main.CliMain;
import org.apache.commons.io.IOUtils;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.skyscreamer.jsonassert.JSONAssert;
import picocli.CommandLine;

import java.io.File;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;


public class ToolsGenerateTest {

    private static final Logger LOG = Logger.getLogger(ToolsGenerate.class);

    private static final int EXIT_CODE_SUCCESS = 0;

    private static final String EXPECTED_TOOLS_FILE = "tools/expectedPetStoreTools.json";

    @TempDir
    Path tempDir;

    @Test
    public void toolGenerateTest() throws Exception {

        CliMain main = new CliMain();
        CommandLine cmd = new CommandLine(main);

        String petStoreJsonOutputPath = tempDir.toString()+"/petStoreJsonTool.json";

        String petStoreYamlOutputPath = tempDir.toString()+"/petStoreYamlTool.json";

        int exitCodePetStoreJson = cmd.execute("tools", "generate", "-o", petStoreJsonOutputPath, "https://petstore3.swagger.io/api/v3/openapi.json");
        int exitCodePetStoreYaml = cmd.execute("tools", "generate", "-o", petStoreYamlOutputPath, "https://petstore3.swagger.io/api/v3/openapi.yaml");

        assertEquals(EXIT_CODE_SUCCESS, exitCodePetStoreJson);
        assertEquals(EXIT_CODE_SUCCESS, exitCodePetStoreYaml);

        Path jsonPath = Paths.get(petStoreJsonOutputPath);
        Path yamlPath = Paths.get(petStoreYamlOutputPath);
        Assertions.assertTrue(Files.exists(jsonPath));
        Assertions.assertTrue(Files.exists(yamlPath));

        String jsonFileContent = Files.readString(jsonPath);

        String yamlFileContent = Files.readString(yamlPath);

        JSONAssert.assertEquals(jsonFileContent, yamlFileContent , true);



        InputStream is = getClass().getClassLoader().getResourceAsStream(EXPECTED_TOOLS_FILE);
        StringWriter writer = new StringWriter();
        IOUtils.copy(is, writer, "UTF-8");
        String expectedPetstoreToolsJsonContent = writer.toString();

        JSONAssert.assertEquals(jsonFileContent, expectedPetstoreToolsJsonContent , true);

    }


}