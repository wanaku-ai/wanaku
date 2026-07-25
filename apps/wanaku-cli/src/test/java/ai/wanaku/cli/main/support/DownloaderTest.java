package ai.wanaku.cli.main.support;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DownloaderTest {

    private HttpServer server;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void shouldDownloadFileWithProgressReporting() throws Exception {
        byte[] content = "Hello, World!".getBytes();
        server.createContext("/test-file.txt", exchange -> {
            exchange.getResponseHeaders().set("Content-Length", String.valueOf(content.length));
            exchange.sendResponseHeaders(200, content.length);
            exchange.getResponseBody().write(content);
            exchange.close();
        });
        server.start();

        String url = "http://localhost:%d/test-file.txt"
                .formatted(server.getAddress().getPort());

        AtomicLong lastBytesRead = new AtomicLong();
        AtomicLong reportedTotal = new AtomicLong();
        List<Long> progressUpdates = new ArrayList<>();

        DownloadProgressListener listener = (bytesRead, totalBytes) -> {
            lastBytesRead.set(bytesRead);
            reportedTotal.set(totalBytes);
            progressUpdates.add(bytesRead);
        };

        File result = Downloader.downloadFile(url, tempDir.toFile(), listener);

        assertTrue(result.exists(), "Downloaded file should exist");
        assertEquals("Hello, World!", Files.readString(result.toPath()));
        assertEquals(content.length, lastBytesRead.get(), "Final bytes read should match content length");
        assertEquals(content.length, reportedTotal.get(), "Reported total should match Content-Length");
        assertFalse(progressUpdates.isEmpty(), "Progress should have been reported at least once");
    }

    @Test
    void shouldDownloadWithoutListenerForBackwardCompatibility() throws Exception {
        byte[] content = "test data".getBytes();
        server.createContext("/compat-file.txt", exchange -> {
            exchange.getResponseHeaders().set("Content-Length", String.valueOf(content.length));
            exchange.sendResponseHeaders(200, content.length);
            exchange.getResponseBody().write(content);
            exchange.close();
        });
        server.start();

        String url = "http://localhost:%d/compat-file.txt"
                .formatted(server.getAddress().getPort());

        File result = Downloader.downloadFile(url, tempDir.toFile());

        assertTrue(result.exists(), "Downloaded file should exist");
        assertEquals("test data", Files.readString(result.toPath()));
    }

    @Test
    void shouldSkipDownloadWhenFileAlreadyExists() throws Exception {
        File existingFile = tempDir.resolve("existing-file.txt").toFile();
        Files.writeString(existingFile.toPath(), "already here");

        AtomicLong callCount = new AtomicLong();
        DownloadProgressListener listener = (bytesRead, totalBytes) -> callCount.incrementAndGet();

        File result = Downloader.downloadFile("http://localhost/existing-file.txt", tempDir.toFile(), listener);

        assertEquals(existingFile, result, "Should return existing file");
        assertEquals(0, callCount.get(), "Progress listener should not be called for cached files");
    }

    @Test
    void shouldHandleMissingContentLength() throws Exception {
        byte[] content = "no content length".getBytes();
        server.createContext("/no-length.txt", exchange -> {
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write(content);
            exchange.close();
        });
        server.start();

        String url = "http://localhost:%d/no-length.txt"
                .formatted(server.getAddress().getPort());

        AtomicLong reportedTotal = new AtomicLong(999);

        File result = Downloader.downloadFile(url, tempDir.toFile(), (bytesRead, totalBytes) -> {
            reportedTotal.set(totalBytes);
        });

        assertTrue(result.exists(), "Downloaded file should exist");
        assertEquals(-1, reportedTotal.get(), "Total should be -1 when Content-Length is absent");
    }
}
