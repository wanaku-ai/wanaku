package ai.wanaku.cli.main.support;

import java.io.PrintWriter;
import java.io.StringWriter;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DownloadProgressBarTest {

    @Test
    void shouldShowPercentageWhenTotalIsKnown() {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        DownloadProgressBar bar = new DownloadProgressBar("test-component", pw);
        bar.onProgress(50, 100);

        String output = sw.toString();
        assertTrue(output.contains("50%"), "Should contain 50%% but was: " + output);
        assertTrue(output.contains("test-component"), "Should contain the label");
    }

    @Test
    void shouldShowBytesWhenTotalIsUnknown() {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        DownloadProgressBar bar = new DownloadProgressBar("test-component", pw);
        bar.onProgress(1024, -1);

        String output = sw.toString();
        assertTrue(output.contains("downloaded"), "Should indicate download progress");
        assertTrue(output.contains("test-component"), "Should contain the label");
    }

    @Test
    void shouldShowFullProgressAt100Percent() {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        DownloadProgressBar bar = new DownloadProgressBar("my-service", pw);
        bar.onProgress(200, 200);

        String output = sw.toString();
        assertTrue(output.contains("100%"), "Should show 100%% but was: " + output);
    }

    @Test
    void finishShouldAddNewline() {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        DownloadProgressBar bar = new DownloadProgressBar("test", pw);
        bar.onProgress(100, 100);
        bar.finish();

        String output = sw.toString();
        assertTrue(output.endsWith(System.lineSeparator()), "Should end with newline");
    }

    @Test
    void formatBytesShouldFormatCorrectly() {
        assertEquals("0 B", DownloadProgressBar.formatBytes(0));
        assertEquals("512 B", DownloadProgressBar.formatBytes(512));
        assertEquals("1.0 KB", DownloadProgressBar.formatBytes(1024));
        assertEquals("1.5 KB", DownloadProgressBar.formatBytes(1536));
        assertEquals("1.0 MB", DownloadProgressBar.formatBytes(1024 * 1024));
        assertEquals("2.5 MB", DownloadProgressBar.formatBytes((long) (2.5 * 1024 * 1024)));
    }
}
