package ai.wanaku.cli.main.support;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import org.jboss.logging.Logger;
import ai.wanaku.capabilities.sdk.api.exceptions.WanakuException;

public class Downloader {
    private static final Logger LOG = Logger.getLogger(Downloader.class);
    private static final int BUFFER_SIZE = 8192;

    private static String getFileNameFromUrl(String url) {
        int lastIndex = url.lastIndexOf("/");
        if (lastIndex != -1) {
            return url.substring(lastIndex + 1);
        }
        throw new WanakuException("Invalid url: " + url);
    }

    /**
     * Downloads a file from the given URL into the specified directory.
     *
     * <p>If the file already exists locally, the download is skipped and the
     * existing file is returned immediately.</p>
     *
     * @param url       the URL to download from
     * @param directory the local directory to save the file into
     * @return the local file
     * @throws MalformedURLException if the URL is invalid
     */
    public static File downloadFile(String url, File directory) throws MalformedURLException {
        return downloadFile(url, directory, null);
    }

    /**
     * Downloads a file from the given URL into the specified directory,
     * reporting progress to the supplied listener.
     *
     * <p>If the file already exists locally, the download is skipped and the
     * existing file is returned immediately (the listener is not called).</p>
     *
     * @param url      the URL to download from
     * @param directory the local directory to save the file into
     * @param listener optional progress listener (may be {@code null})
     * @return the local file
     * @throws MalformedURLException if the URL is invalid
     */
    public static File downloadFile(String url, File directory, DownloadProgressListener listener)
            throws MalformedURLException {
        String fileName = getFileNameFromUrl(url);
        File localFile = new File(directory, fileName);
        if (localFile.exists()) {
            LOG.infof("Local file %s already exists", fileName);
            return localFile;
        }

        try {
            File parentDir = localFile.getParentFile();
            Files.createDirectories(parentDir.toPath());

            HttpClient client = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();

            HttpRequest request =
                    HttpRequest.newBuilder().uri(URI.create(url)).GET().build();

            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

            int statusCode = response.statusCode();
            if (statusCode < 200 || statusCode >= 300) {
                throw new WanakuException("Download failed with HTTP status " + statusCode + " for URL: " + url);
            }

            long totalBytes =
                    response.headers().firstValueAsLong("Content-Length").orElse(-1L);

            try (InputStream in = response.body();
                    OutputStream out = new FileOutputStream(localFile)) {

                byte[] buffer = new byte[BUFFER_SIZE];
                long bytesRead = 0;
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    bytesRead += read;
                    if (listener != null) {
                        listener.onProgress(bytesRead, totalBytes);
                    }
                }
            }
        } catch (IOException e) {
            // Clean up partial file on failure
            if (localFile.exists()) {
                localFile.delete();
            }
            throw new WanakuException(e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (localFile.exists()) {
                localFile.delete();
            }
            throw new WanakuException(e);
        }
        LOG.infof("File downloaded successfully to: %s", localFile.getAbsolutePath());
        return localFile;
    }
}
