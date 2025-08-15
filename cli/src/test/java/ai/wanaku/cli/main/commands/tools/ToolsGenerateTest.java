package ai.wanaku.cli.main.commands.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ai.wanaku.cli.main.CliMain;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import org.apache.commons.io.IOUtils;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.skyscreamer.jsonassert.JSONAssert;
import picocli.CommandLine;

public class ToolsGenerateTest {

    private static final Logger LOG = Logger.getLogger(ToolsGenerate.class);

    private static final int EXIT_CODE_SUCCESS = 0;

    private static final String EXPECTED_TOOLS_FILE = "tools/expectedPetStoreTools.json";

    private static final String YAHOO_FINANCE_EXPECTED_TOOLS_FILE = "tools/expectedPetStoreTools.json";

    @TempDir
    Path tempDir;

    /**
     *  issue292  https://github.com/wanaku-ai/wanaku/issues/292
     */
    @Test
    public void toolGenerateYahooFinanceTest() throws Exception {
        CliMain main = new CliMain();
        CommandLine cmd = new CommandLine(main);

        // expectations:
        URL resourceUrl = getClass().getClassLoader().getResource("tools/expected-yahoo-finance-tools.json");

        String tmpDirPathString = tempDir.toString();

        Path resourceFilePath = Path.of(resourceUrl.toURI());
        String expectedToolsDefinition = Files.readString(resourceFilePath);

        String yahooFinanceOutputPathString = tmpDirPathString + "/yahooFinanceJsonTool.json";

        String yahooFinanceInputPathString = copyFromResourceToTempDir("openapi", "yahoo-finance.yml");

        Path yahooFinanceInputPath = Paths.get(yahooFinanceInputPathString);

        int exitCodeYahooJson = cmd.execute(
                "tools",
                "generate",
                "-o",
                yahooFinanceOutputPathString,
                yahooFinanceInputPath.toAbsolutePath().toString());

        assertEquals(EXIT_CODE_SUCCESS, exitCodeYahooJson);
        Path jsonPath = Paths.get(yahooFinanceOutputPathString);

        String content = Files.readString(jsonPath);

        JSONAssert.assertEquals(content, expectedToolsDefinition, true);
    }

    @Test
    public void toolGenerateTest() throws Exception {

        CliMain main = new CliMain();
        CommandLine cmd = new CommandLine(main);
        String tmpDirPathString = tempDir.toString();

        // expectation:
        String petStoreJsonOutputPath = tmpDirPathString + "/petStoreJsonTool.json";
        String petStoreYamlOutputPath = tmpDirPathString + "/petStoreYamlTool.json";

        String petStoreJsonPathString = copyFromResourceToTempDir("openapi", "pet-store.json");
        String petStoreYamlPathString = copyFromResourceToTempDir("openapi", "pet-store.yaml");

        int exitCodePetStoreJson = cmd.execute(
                "tools",
                "generate",
                "-u",
                "https://petstore3.swagger.io/api/v3",
                "-o",
                petStoreJsonOutputPath,
                petStoreJsonPathString);
        int exitCodePetStoreYaml = cmd.execute(
                "tools",
                "generate",
                "-u",
                "https://petstore3.swagger.io/api/v3",
                "-o",
                petStoreYamlOutputPath,
                petStoreYamlPathString);

        assertEquals(EXIT_CODE_SUCCESS, exitCodePetStoreJson);
        assertEquals(EXIT_CODE_SUCCESS, exitCodePetStoreYaml);

        Path jsonPath = Paths.get(petStoreJsonOutputPath);
        Path yamlPath = Paths.get(petStoreYamlOutputPath);

        Assertions.assertTrue(Files.exists(jsonPath));
        Assertions.assertTrue(Files.exists(yamlPath));

        String jsonFileContent = Files.readString(jsonPath);
        String yamlFileContent = Files.readString(yamlPath);

        JSONAssert.assertEquals(jsonFileContent, yamlFileContent, true);

        InputStream is = getClass().getClassLoader().getResourceAsStream(EXPECTED_TOOLS_FILE);
        StringWriter writer = new StringWriter();
        IOUtils.copy(is, writer, "UTF-8");
        String expectedPetstoreToolsJsonContent = writer.toString();
        JSONAssert.assertEquals(jsonFileContent, expectedPetstoreToolsJsonContent, true);
    }

    private String copyFromResourceToTempDir(String resourcePath, String resource) throws IOException {
        InputStream resourceStream = getClass().getClassLoader().getResourceAsStream(resourcePath + "/" + resource);
        Path tempDirDestinationPath = Paths.get(tempDir.toString(), resource);
        Files.copy(resourceStream, tempDirDestinationPath, StandardCopyOption.REPLACE_EXISTING);
        return tempDirDestinationPath.toString();
    }
}
