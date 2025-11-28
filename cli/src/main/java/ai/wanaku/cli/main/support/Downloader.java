package ai.wanaku.cli.main.support;

import ai.wanaku.capabilities.sdk.api.exceptions.WanakuException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import org.jboss.logging.Logger;

public class Downloader {
    private static final Logger LOG = Logger.getLogger(Downloader.class);

    private static String getFileNameFromUrl(String url) {
        int lastIndex = url.lastIndexOf("/");
        if (lastIndex != -1) {
            return url.substring(lastIndex + 1);
        }
        throw new WanakuException("Invalid url: " + url);
    }

    public static File downloadFile(String url, File directory) throws MalformedURLException {
        String fileName = getFileNameFromUrl(url);
        File localFile = new File(directory, fileName);
        if (localFile.exists()) {
            LOG.infof("Local file %s already exists", fileName);
            return localFile;
        }

        URL remoteFile = new URL(url);

        try (InputStream in = remoteFile.openStream()) {
            File parentDir = localFile.getParentFile();
            Files.createDirectories(parentDir.toPath());

            try (OutputStream out = new FileOutputStream(localFile)) {
                in.transferTo(out);
            }

        } catch (IOException e) {
            throw new WanakuException(e);
        }
        LOG.infof("File downloaded successfully to: %s", localFile.getAbsolutePath());
        return localFile;
    }
}
