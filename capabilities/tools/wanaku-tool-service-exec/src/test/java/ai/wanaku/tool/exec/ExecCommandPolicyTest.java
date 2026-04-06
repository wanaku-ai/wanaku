package ai.wanaku.tool.exec;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExecCommandPolicyTest {

    @TempDir
    Path tempDir;

    @Test
    void buildsAllowlistedCommandWithQuotedArguments() throws Exception {
        Path scriptPath = createScript(tempDir, "policy-ok");

        ExecCommandPolicy policy = new ExecCommandPolicy(List.of(scriptPath.toString()));

        List<String> command = policy.buildCommand("\"" + scriptPath + "\" --mode \"hello world\"");

        assertEquals(List.of(scriptPath.toAbsolutePath().normalize().toString(), "--mode", "hello world"), command);
    }

    @Test
    void rejectsNonAllowlistedExecutable() throws Exception {
        Path scriptPath = createScript(tempDir, "policy-denied");

        ExecCommandPolicy policy = new ExecCommandPolicy(List.of("/bin/does-not-exist"));

        assertThrows(SecurityException.class, () -> policy.buildCommand(scriptPath.toString()));
    }

    @Test
    void rejectsShellMetacharacters() throws Exception {
        Path scriptPath = createScript(tempDir, "policy-meta");

        ExecCommandPolicy policy = new ExecCommandPolicy(List.of(scriptPath.toString()));

        assertThrows(SecurityException.class, () -> policy.buildCommand(scriptPath + " ; whoami"));
    }

    private static Path createScript(Path dir, String marker) throws IOException {
        Files.createDirectories(dir);

        boolean isWindows =
                System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");

        String extension = isWindows ? ".cmd" : ".sh";
        Path scriptPath = dir.resolve("exec-it-" + marker + extension).toAbsolutePath();

        String content =
                isWindows ? "@echo off\r\necho " + marker + "\r\n" : "#!/usr/bin/env sh\n" + "echo " + marker + "\n";

        Files.writeString(scriptPath, content, StandardCharsets.US_ASCII);

        if (!isWindows) {
            try {
                Set<PosixFilePermission> permissions = PosixFilePermissions.fromString("rwxr-xr-x");
                Files.setPosixFilePermissions(scriptPath, permissions);
            } catch (UnsupportedOperationException ignored) {
            }
        }

        return scriptPath;
    }
}
