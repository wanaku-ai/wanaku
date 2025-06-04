package ai.wanaku.core.util;

import ai.wanaku.api.types.ToolReference;
import ai.wanaku.core.util.support.ToolsHelper;
import java.io.File;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static ai.wanaku.core.util.support.ToolsHelper.TOOLSET_INDEX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ToolsetIndexHelperTest {
    public static final List<ToolReference> TOOL_REFERENCES = ToolsHelper.testFixtures();


    @BeforeAll
    static void setup() {
        File indexFile = new File(TOOLSET_INDEX);
        if (!indexFile.exists()) {
            indexFile.delete();
        }
    }

    @Order(1)
    @Test
    public void testSaveToolsIndex() throws IOException {
        // Create a temporary index file
        File indexFile = new File(TOOLSET_INDEX);

        // Save the index to a file
        ToolsetIndexHelper.saveToolsIndex(indexFile, TOOL_REFERENCES);

        // Verify that the file exists and is not empty
        assertTrue(indexFile.exists());
        assertTrue(indexFile.length() > 0);
    }

    @Order(2)
    @Test
    public void testLoadToolsIndex() throws Exception {
        // Use the temporary index file
        File indexFile = new File(TOOLSET_INDEX);

        // Save the index to a file
        ToolsetIndexHelper.loadToolsIndex(indexFile);

        // Load the index back from the file
        List<ToolReference> loadedToolsReferences = ToolsetIndexHelper.loadToolsIndex(indexFile);

        // Verify that the loaded resources match the original ones
        assertEquals(TOOL_REFERENCES.size(), loadedToolsReferences.size());
        for (int i = 0; i < loadedToolsReferences.size(); i++) {
            ToolReference expected = TOOL_REFERENCES.get(i);
            ToolReference actual = loadedToolsReferences.get(i);
            assertEquals(expected, actual);
        }
    }

    @Order(3)
    @Test
    public void testLoadToolsIndexThrowsException() {
        // Create a non-existent index file
        File indexFile = new File("non-existent-index.json");

        assertThrows(Exception.class, () -> ToolsetIndexHelper.loadToolsIndex(indexFile));
    }
}