package ai.wanaku.server.quarkus.api.v1.management.targets;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import ai.wanaku.api.types.management.Service;
import ai.wanaku.core.util.IndexHelper;
import org.wanaku.server.quarkus.support.TargetsHelper;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.wanaku.server.quarkus.support.TargetsHelper.RESOURCE_TARGETS_INDEX;
import static org.wanaku.server.quarkus.support.TargetsHelper.TOOLS_TARGETS_INDEX;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ServiceRegistrySaveTest {
    @Order(7)
    @Test
    public void testSaveTargetsIndex() throws IOException {
        // Create a temporary index file
        File indexFile = new File(RESOURCE_TARGETS_INDEX);

        // Save the index to a file
        IndexHelper.saveTargetsIndex(indexFile, TargetsHelper.getResourceTargets());

        // Verify that the file exists and is not empty
        assertTrue(indexFile.exists());
        assertTrue(indexFile.length() > 0);
    }

    @Order(8)
    @Test
    public void testLoadTargetsIndex() throws Exception {
        // Use the temporary index file
        File indexFile = new File(RESOURCE_TARGETS_INDEX);

        // Save the index to a file
        Map<String, Service> targets = IndexHelper.loadTargetsIndex(indexFile, Service.class);

        assertEquals(1, targets.size());
    }

    @Order(9)
    @Test
    public void testSaveToolsTargetsIndex() throws IOException {
        // Create a temporary index file
        File indexFile = new File(TOOLS_TARGETS_INDEX);

        // Save the index to a file
        IndexHelper.saveTargetsIndex(indexFile, TargetsHelper.getToolsTargets());

        // Verify that the file exists and is not empty
        assertTrue(indexFile.exists());
        assertTrue(indexFile.length() > 0);
    }

    @Order(10)
    @Test
    public void testLoadToolsTargetsIndex() throws Exception {
        // Use the temporary index file
        File indexFile = new File(TOOLS_TARGETS_INDEX);

        // Save the index to a file
        Map<String, Service> targets = IndexHelper.loadTargetsIndex(indexFile, Service.class);

        assertEquals(2, targets.size());
    }
}
