package ai.wanaku.cli.main.support;

/**
 * Callback interface for tracking download progress.
 *
 * <p>Implementations receive periodic updates as bytes are downloaded, allowing
 * them to render progress bars, log messages, or other feedback.</p>
 */
@FunctionalInterface
public interface DownloadProgressListener {

    /**
     * Called periodically during a download to report progress.
     *
     * @param bytesRead total bytes downloaded so far
     * @param totalBytes total expected size in bytes, or {@code -1} if unknown
     */
    void onProgress(long bytesRead, long totalBytes);
}
