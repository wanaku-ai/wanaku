package ai.wanaku.server.quarkus.support;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import ai.wanaku.core.util.IndexHelper;
import ai.wanaku.core.util.support.ResourcesHelper;
import ai.wanaku.core.util.support.ToolsHelper;
import ai.wanaku.server.quarkus.api.v1.resources.ResourcesResourceTest;
import ai.wanaku.server.quarkus.api.v1.tools.ToolsResourceTest;

public class TestIndexHelper {
    public static File createToolsIndex() throws IOException {
        File indexFile = new File(ToolsHelper.TOOLS_INDEX);
        if (!indexFile.getParentFile().exists()) {
            indexFile.getParentFile().mkdirs();
        }

        indexFile.deleteOnExit();

        if (indexFile.exists() && !Files.readString(indexFile.toPath()).equals("[]")) {
            return indexFile;
        }

        // Save the index to a file
        IndexHelper.saveToolsIndex(indexFile, ToolsResourceTest.TOOL_REFERENCES);
        return indexFile;
    }

    public static File createResourcesIndex() throws IOException {
        File indexFile = new File(ResourcesHelper.RESOURCES_INDEX);
        if (!indexFile.getParentFile().exists()) {
            indexFile.getParentFile().mkdirs();
        }

        indexFile.deleteOnExit();

        if (indexFile.exists() && !Files.readString(indexFile.toPath()).equals("[]")) {
            return indexFile;
        }

        // Save the index to a file
        IndexHelper.saveResourcesIndex(indexFile, ResourcesResourceTest.RESOURCE_REFERENCES);
        return indexFile;
    }
}
