package ai.wanaku.tool.exec;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import ai.wanaku.capabilities.sdk.config.provider.api.ConfigResource;
import ai.wanaku.core.capabilities.common.ConfigResourceLoader;
import ai.wanaku.core.exchange.v1.ToolInvokeRequest;
import ai.wanaku.core.util.RuntimeInfo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecClientIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void executesScriptAndReturnsOutput() throws Exception {
        Path scriptPath = createScript(tempDir, "exec-it-ok");

        ToolInvokeRequest request =
                ToolInvokeRequest.newBuilder().setUri(scriptPath.toString()).build();
        ConfigResource configResource = ConfigResourceLoader.loadFromRequest(request);

        Object response = newClient(scriptPath).exchange(request, configResource);

        assertNotNull(response);

        String output = normalizeOutput(response);
        assertTrue(output.contains("exec-it-ok"));
    }

    @Test
    void executesScriptFromTemplateUri() throws Exception {
        Path scriptPath = createScript(tempDir, "exec-it-template");

        ToolInvokeRequest request = ToolInvokeRequest.newBuilder()
                .setUri("{path}")
                .putArguments("path", scriptPath.toString())
                .build();

        ConfigResource configResource = ConfigResourceLoader.loadFromRequest(request);

        Object response = newClient(scriptPath).exchange(request, configResource);

        assertNotNull(response);

        String output = normalizeOutput(response);
        assertTrue(output.contains("exec-it-template"));
    }

    private static ExecClient newClient(Path scriptPath) {
        return new ExecClient(
                new ExecCommandPolicy(List.of(scriptPath.toString())), new ProcessRunnerCommandExecutor());
    }

    private static Path createScript(Path dir, String marker) throws IOException {
        Files.createDirectories(dir);

        boolean isWindows = RuntimeInfo.isWindows();

        String extension = isWindows ? ".cmd" : ".sh";
        Path scriptPath = dir.resolve("exec-it-" + marker + extension).toAbsolutePath();

        String content =
                isWindows ? "@echo off\r\necho " + marker + "\r\n" : "#!/usr/bin/env sh\n" + "echo " + marker + "\n";

        Files.writeString(scriptPath, content, StandardCharsets.UTF_8);

        if (!isWindows) {
            try {
                Set<PosixFilePermission> permissions = PosixFilePermissions.fromString("rwxr-xr-x");
                Files.setPosixFilePermissions(scriptPath, permissions);
            } catch (UnsupportedOperationException ignored) {
            }
        }

        return scriptPath;
    }

    private static String normalizeOutput(Object response) {
        if (response == null) {
            return "";
        }
        if (response instanceof List<?> list) {
            return list.stream().map(Object::toString).collect(Collectors.joining("\n"));
        }
        return response.toString();
    }
}
