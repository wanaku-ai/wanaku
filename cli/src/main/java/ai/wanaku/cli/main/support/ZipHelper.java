package ai.wanaku.cli.main.support;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.jboss.logging.Logger;

public class ZipHelper {
    private static final Logger LOG = Logger.getLogger(ZipHelper.class);

    public static void unzip(File zipFilePath, String destination, String componentName) throws IOException {
        unzip(zipFilePath, new File(destination), componentName);
    }

    public static void unzip(File zipFilePath, File destination, String componentName) throws IOException {
        // Create a ZipInputStream object from the specified ZIP file
        try (ZipFile zipFile = new ZipFile(zipFilePath)) {

            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            // Iterate over each entry in the ZIP file
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();

                LOG.debugf("Unpacking %s", entry.getName());

                String name = finalEntryName(componentName, entry);

                // Create a file for the current entry in the unzip folder
                File unzippedFile = new File(destination, name);

                if (!entry.isDirectory()) {
                    // Open an input stream to read from the entry
                    try (InputStream in = zipFile.getInputStream(entry)) {
                        // Open an output stream to write to the local file
                        try (OutputStream out = new FileOutputStream(unzippedFile)) {
                            in.transferTo(out);

                        } catch (IOException e) {
                            LOG.errorf(e,"Error writing to file: %s", e.getMessage());
                        }
                    } catch (IOException e) {
                        LOG.errorf(e, "Error reading from entry: %s", e.getMessage());
                    }
                } else {
                    if (unzippedFile.exists()) {
                        return;
                    }

                    Files.createDirectories(unzippedFile.toPath());
                }
            }
        }

        LOG.infof("ZIP file contents extracted successfully to: %s", destination.getAbsolutePath());
    }

    /**
     * Remove the first part of the entry name, so that the directories do not contain the version part
     * @param componentName
     * @param entry
     * @return
     */
    private static String finalEntryName(String componentName, ZipEntry entry) {
        int idx = entry.getName().indexOf("/");
        String entryName = entry.getName().substring(idx);
        return String.format("%s/%s", componentName, entryName);
    }
}
