/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ai.wanaku.core.util;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import ai.wanaku.api.types.ResourceReference;
import ai.wanaku.api.types.ToolReference;
import ai.wanaku.core.util.support.ResourcesHelper;
import ai.wanaku.core.util.support.TargetsHelper;
import ai.wanaku.core.util.support.ToolsHelper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static ai.wanaku.core.util.support.ResourcesHelper.RESOURCES_INDEX;
import static ai.wanaku.core.util.support.TargetsHelper.RESOURCE_TARGETS_INDEX;
import static ai.wanaku.core.util.support.TargetsHelper.TOOLS_TARGETS_INDEX;
import static ai.wanaku.core.util.support.ToolsHelper.TOOLS_INDEX;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class IndexHelperTest {
    public static final List<ResourceReference> RESOURCE_REFERENCES = ResourcesHelper.testFixtures();
    public static final List<ToolReference> TOOL_REFERENCES = ToolsHelper.testFixtures();


    @BeforeAll
    static void setup() {
        File indexFile = new File(RESOURCES_INDEX);
        if (!indexFile.exists()) {
            indexFile.delete();
        }
    }

    @Order(1)
    @Test
    public void testSaveResourcesIndex() throws IOException {
        // Create a temporary index file
        File indexFile = new File(RESOURCES_INDEX);

        // Save the index to a file
        IndexHelper.saveResourcesIndex(indexFile, RESOURCE_REFERENCES);

        // Verify that the file exists and is not empty
        assertTrue(indexFile.exists());
        assertTrue(indexFile.length() > 0);
    }

    @Order(2)
    @Test
    public void testLoadResourcesIndex() throws Exception {
        // Use the temporary index file
        File indexFile = new File(RESOURCES_INDEX);

        // Save the index to a file
        IndexHelper.loadResourcesIndex(indexFile);

        // Load the index back from the file
        List<ResourceReference> loadedResourceReferences = IndexHelper.loadResourcesIndex(indexFile);

        // Verify that the loaded resources match the original ones
        assertEquals(loadedResourceReferences.size(), loadedResourceReferences.size());
        for (int i = 0; i < loadedResourceReferences.size(); i++) {
            ResourceReference expected = RESOURCE_REFERENCES.get(i);
            ResourceReference actual = loadedResourceReferences.get(i);
            assertEquals(expected.getLocation(), actual.getLocation());
            assertEquals(expected.getType(), actual.getType());
            assertEquals(expected.getName(), actual.getName());
        }
    }

    @Order(3)
    @Test
    public void testLoadResourcesIndexThrowsException() {
        // Create a non-existent index file
        File indexFile = new File("non-existent-index.json");

        assertThrows(Exception.class, () -> IndexHelper.loadResourcesIndex(indexFile));
    }

    @Order(4)
    @Test
    public void testSaveToolsIndex() throws IOException {
        // Create a temporary index file
        File indexFile = new File(TOOLS_INDEX);

        // Save the index to a file
        IndexHelper.saveToolsIndex(indexFile, TOOL_REFERENCES);

        // Verify that the file exists and is not empty
        assertTrue(indexFile.exists());
        assertTrue(indexFile.length() > 0);
    }

    @Order(5)
    @Test
    public void testLoadToolsIndex() throws Exception {
        // Use the temporary index file
        File indexFile = new File(TOOLS_INDEX);

        // Save the index to a file
        IndexHelper.loadToolsIndex(indexFile);

        // Load the index back from the file
        List<ToolReference> loadedToolsReferences = IndexHelper.loadToolsIndex(indexFile);

        // Verify that the loaded resources match the original ones
        assertEquals(loadedToolsReferences.size(), loadedToolsReferences.size());
        for (int i = 0; i < loadedToolsReferences.size(); i++) {
            ToolReference expected = TOOL_REFERENCES.get(i);
            ToolReference actual = loadedToolsReferences.get(i);
            assertEquals(expected, actual);
        }
    }

    @Order(6)
    @Test
    public void testLoadToolsIndexThrowsException() {
        // Create a non-existent index file
        File indexFile = new File("non-existent-index.json");

        assertThrows(Exception.class, () -> IndexHelper.loadToolsIndex(indexFile));
    }

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
        Map<String, String> targets = IndexHelper.loadTargetsIndex(indexFile);

        assertEquals(1, targets.size());
    }

    @Order(9)
    @Test
    public void testSaveToolsTargetsIndex() throws IOException {
        // Create a temporary index file
        File indexFile = new File(TOOLS_TARGETS_INDEX);

        // Save the index to a file
        IndexHelper.saveTargetsIndex(indexFile, TargetsHelper.getResourceTargets());

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
        Map<String, String> targets = IndexHelper.loadTargetsIndex(indexFile);

        assertEquals(1, targets.size());
    }
}