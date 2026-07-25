package ai.wanaku.cli.main.support;

import java.io.PrintWriter;

/**
 * Renders a text-based progress bar for file downloads.
 *
 * <p>Overwrites the current terminal line using carriage return ({@code \r})
 * to provide a live-updating progress display.  When the total size is known
 * a percentage bar is shown; otherwise only the downloaded byte count is
 * displayed.</p>
 */
public final class DownloadProgressBar implements DownloadProgressListener {

    private static final int BAR_WIDTH = 30;

    private final String label;
    private final PrintWriter writer;

    /**
     * Creates a progress bar with the given label and writer.
     *
     * @param label  short name shown before the bar (e.g. component name)
     * @param writer the writer to print progress to (typically terminal writer)
     */
    public DownloadProgressBar(String label, PrintWriter writer) {
        this.label = label;
        this.writer = writer;
    }

    @Override
    public void onProgress(long bytesRead, long totalBytes) {
        if (totalBytes > 0) {
            int percent = (int) (bytesRead * 100 / totalBytes);
            int filled = (int) (bytesRead * BAR_WIDTH / totalBytes);
            int empty = BAR_WIDTH - filled;

            writer.printf(
                    "\r%-25s [%s%s] %3d%% (%s / %s)",
                    label,
                    "=".repeat(filled),
                    " ".repeat(empty),
                    percent,
                    formatBytes(bytesRead),
                    formatBytes(totalBytes));
        } else {
            writer.printf("\r%-25s  %s downloaded", label, formatBytes(bytesRead));
        }
        writer.flush();
    }

    /**
     * Prints a newline to move past the progress bar after download completes.
     */
    public void finish() {
        writer.println();
        writer.flush();
    }

    static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        }
    }
}
