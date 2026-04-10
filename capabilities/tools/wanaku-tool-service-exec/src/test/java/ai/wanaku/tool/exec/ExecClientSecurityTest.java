package ai.wanaku.tool.exec;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Set;
import ai.wanaku.capabilities.sdk.config.provider.api.ConfigResource;
import ai.wanaku.core.capabilities.common.ConfigResourceLoader;
import ai.wanaku.core.exchange.v1.ToolInvokeRequest;
import ai.wanaku.core.util.RuntimeInfo;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExecClientSecurityTest {

    @TempDir
    Path tempDir;

    @Test
    void doesNotInvokeExecutorWhenExecutableIsNotAllowlisted() throws Exception {
        Path allowlistedExecutable = createScript(tempDir, "allowed");

        RecordingExecutor executor = new RecordingExecutor();
        ExecClient client = new ExecClient(new ExecCommandPolicy(List.of(allowlistedExecutable.toString())), executor);

        ToolInvokeRequest request = ToolInvokeRequest.newBuilder()
                .setUri(createScript(tempDir, "denied").toString() + " --version")
                .build();
        ConfigResource configResource = ConfigResourceLoader.loadFromRequest(request);

        assertThrows(SecurityException.class, () -> client.exchange(request, configResource));
        assertNull(executor.command);
    }

    @Test
    void passesAllowlistedCommandToExecutor() throws Exception {
        Path allowlistedExecutable = createScript(tempDir, "allowed");

        RecordingExecutor executor = new RecordingExecutor();
        ExecClient client = new ExecClient(new ExecCommandPolicy(List.of(allowlistedExecutable.toString())), executor);

        ToolInvokeRequest request = ToolInvokeRequest.newBuilder()
                .setUri(allowlistedExecutable.toString() + " --version")
                .build();
        ConfigResource configResource = ConfigResourceLoader.loadFromRequest(request);

        client.exchange(request, configResource);

        assertArrayEquals(
                new String[] {allowlistedExecutable.toAbsolutePath().normalize().toString(), "--version"},
                executor.command);
    }

    private static Path createScript(Path dir, String marker) throws IOException {
        Files.createDirectories(dir);

        boolean isWindows = RuntimeInfo.isWindows();

        String extension = isWindows ? ".cmd" : ".sh";
        Path scriptPath = dir.resolve("exec-security-" + marker + extension).toAbsolutePath();

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

    private static final class RecordingExecutor implements CommandExecutor {
        private String[] command;

        @Override
        public Object run(String[] command) {
            this.command = command;
            return List.of(command);
        }
    }
}
