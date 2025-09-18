package ai.wanaku.backend.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

public class TestIndexHelper {
    public static void deleteRecursively(String baseDir) throws IOException {
        Path dir = Paths.get(baseDir);
        if (!dir.toFile().exists()) {
            return;
        }

        Files.walk(dir).sorted(Comparator.reverseOrder()).forEach(path -> {
            try {
                Files.delete(path);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }
}
