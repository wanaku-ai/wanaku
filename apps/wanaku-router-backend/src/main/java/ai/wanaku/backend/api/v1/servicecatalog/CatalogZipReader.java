package ai.wanaku.backend.api.v1.servicecatalog;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import ai.wanaku.capabilities.sdk.api.exceptions.WanakuException;
import ai.wanaku.core.services.api.SafeZip;

final class CatalogZipReader {

    private CatalogZipReader() {}

    static Map<String, byte[]> readEntries(byte[] zipBytes) throws WanakuException {
        try {
            return doRead(zipBytes);
        } catch (IOException e) {
            throw new WanakuException("Failed to read ZIP contents: %s".formatted(e.getMessage()));
        }
    }

    static Map<String, String> readEntriesAsText(byte[] zipBytes) throws WanakuException {
        return toTextView(readEntries(zipBytes));
    }

    static Map<String, String> toTextView(Map<String, byte[]> entries) {
        Map<String, String> text = new HashMap<>(entries.size());
        for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
            text.put(entry.getKey(), new String(entry.getValue(), StandardCharsets.UTF_8));
        }
        return text;
    }

    private static Map<String, byte[]> doRead(byte[] zipBytes) throws IOException {
        Map<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            int entryCount = 0;
            long totalBytes = 0;
            while ((entry = zis.getNextEntry()) != null) {
                if (++entryCount > SafeZip.MAX_ENTRIES) {
                    throw new IOException("ZIP has too many entries (max %d)".formatted(SafeZip.MAX_ENTRIES));
                }
                if (!entry.isDirectory()) {
                    byte[] content = SafeZip.readEntry(zis, SafeZip.MAX_ENTRY_BYTES);
                    totalBytes += content.length;
                    if (totalBytes > SafeZip.MAX_TOTAL_UNCOMPRESSED_BYTES) {
                        throw new IOException("ZIP uncompressed size exceeds the maximum of %d bytes"
                                .formatted(SafeZip.MAX_TOTAL_UNCOMPRESSED_BYTES));
                    }
                    entries.put(entry.getName(), content);
                }
                zis.closeEntry();
            }
        }
        return entries;
    }
}
