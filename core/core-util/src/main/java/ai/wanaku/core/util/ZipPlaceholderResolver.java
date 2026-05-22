package ai.wanaku.core.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import ai.wanaku.capabilities.sdk.api.exceptions.WanakuException;

/**
 * Resolves placeholders inside selected files of a ZIP archive without touching
 * the original source files on disk.
 */
public final class ZipPlaceholderResolver {

    private ZipPlaceholderResolver() {}

    /**
     * Result of resolving placeholders inside a ZIP archive.
     */
    public static final class ResolutionResult {
        private final byte[] zipBytes;
        private final boolean changed;

        private ResolutionResult(byte[] zipBytes, boolean changed) {
            this.zipBytes = zipBytes;
            this.changed = changed;
        }

        public byte[] getZipBytes() {
            return zipBytes;
        }

        public boolean isChanged() {
            return changed;
        }
    }

    /**
     * Resolve placeholders in the given ZIP entries using the supplied bindings.
     *
     * @param zipBytes the original ZIP archive bytes
     * @param entryNames the ZIP entry names that should be processed
     * @param bindings placeholder bindings loaded from a versions file
     * @return the original ZIP bytes when nothing changed, or a rebuilt ZIP otherwise
     * @throws WanakuException if the ZIP cannot be read or written
     */
    public static ResolutionResult resolveEntries(
            byte[] zipBytes, Collection<String> entryNames, Map<String, String> bindings) throws WanakuException {
        if (zipBytes == null || entryNames == null || entryNames.isEmpty() || bindings == null || bindings.isEmpty()) {
            return new ResolutionResult(zipBytes, false);
        }

        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entries.put(entry.getName(), zis.readAllBytes());
                zis.closeEntry();
            }
        } catch (IOException e) {
            throw new WanakuException("Failed to read ZIP archive: " + e.getMessage());
        }

        Set<String> selectedEntries = Set.copyOf(entryNames);
        boolean changed = false;
        for (String entryName : selectedEntries) {
            byte[] contentBytes = entries.get(entryName);
            if (contentBytes == null) {
                continue;
            }

            String content = new String(contentBytes, StandardCharsets.UTF_8);
            String resolved = PlaceholderResolver.resolve(content, bindings);
            if (!resolved.equals(content)) {
                entries.put(entryName, resolved.getBytes(StandardCharsets.UTF_8));
                changed = true;
            }
        }

        if (!changed) {
            return new ResolutionResult(zipBytes, false);
        }

        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(entry.getKey()));
                zos.write(entry.getValue());
                zos.closeEntry();
            }
            return new ResolutionResult(baos.toByteArray(), true);
        } catch (IOException e) {
            throw new WanakuException("Failed to write resolved ZIP archive: " + e.getMessage());
        }
    }

    /**
     * Resolve placeholders in the ZIP entries selected by the given list.
     *
     * @param zipBytes the original ZIP archive bytes
     * @param entryNames the ZIP entry names that should be processed
     * @param bindings placeholder bindings loaded from a versions file
     * @return the resolved ZIP bytes
     * @throws WanakuException if the ZIP cannot be processed
     */
    public static byte[] resolveEntriesToBytes(
            byte[] zipBytes, Collection<String> entryNames, Map<String, String> bindings) throws WanakuException {
        return resolveEntries(zipBytes, entryNames, bindings).getZipBytes();
    }
}
