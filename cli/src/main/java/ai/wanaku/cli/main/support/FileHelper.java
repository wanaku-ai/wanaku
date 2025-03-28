package ai.wanaku.cli.main.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileHelper {

    private FileHelper() {}

    public static boolean canWriteToDirectory(Path dir) {
        if (dir == null) {
            return false;
        }
        try {
            Path tempFile = Files.createTempFile(dir, "wanaku-test", ".tmp");
            Files.delete(tempFile);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static boolean cannotWriteToDirectory(Path dir) {
        return !canWriteToDirectory(dir);
    }
}
