/*
 * Copyright 2026 Wanaku AI
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ai.wanaku.core.services.api;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.zip.ZipInputStream;
import ai.wanaku.capabilities.sdk.api.exceptions.WanakuException;

/**
 * Bounds for decoding and reading uploaded ZIP archives, guarding against decompression-bomb /
 * out-of-memory denial-of-service. The limits are deliberately generous for legitimate service
 * catalogs and templates while rejecting pathological archives.
 */
public final class SafeZip {

    /** Maximum size of a decoded ZIP archive, in bytes. */
    public static final long MAX_ARCHIVE_BYTES = 50L * 1024 * 1024; // 50 MiB

    /** Maximum number of entries allowed in an archive. */
    public static final int MAX_ENTRIES = 10_000;

    /** Maximum uncompressed size of a single entry, in bytes. */
    public static final long MAX_ENTRY_BYTES = 50L * 1024 * 1024; // 50 MiB

    /** Maximum total uncompressed size across all entries of an archive, in bytes. */
    public static final long MAX_TOTAL_UNCOMPRESSED_BYTES = 200L * 1024 * 1024; // 200 MiB

    private SafeZip() {}

    /**
     * Base64-decodes an archive, rejecting input whose decoded size would exceed
     * {@link #MAX_ARCHIVE_BYTES}.
     *
     * @param base64Data the Base64-encoded archive
     * @return the decoded bytes
     * @throws WanakuException if the data is missing, too large, or not valid Base64
     */
    public static byte[] decodeArchive(String base64Data) throws WanakuException {
        if (base64Data == null) {
            throw new WanakuException("Missing archive data");
        }
        // A Base64 string decodes to ~3/4 of its length; reject early, before allocating.
        long approxDecoded = (long) (base64Data.length() / 4) * 3;
        if (approxDecoded > MAX_ARCHIVE_BYTES) {
            throw new WanakuException(
                    "Archive exceeds the maximum allowed size of %d bytes".formatted(MAX_ARCHIVE_BYTES));
        }
        try {
            return Base64.getDecoder().decode(base64Data);
        } catch (IllegalArgumentException e) {
            throw new WanakuException("Invalid Base64 data: " + e.getMessage());
        }
    }

    /**
     * Reads the current ZIP entry fully, failing if it expands beyond {@code maxBytes}. This guards
     * against decompression bombs, where a small compressed entry expands to a huge uncompressed
     * size — unlike {@link ZipInputStream#readAllBytes()}, which is unbounded.
     *
     * @param zis the zip input stream positioned at an entry
     * @param maxBytes the maximum number of uncompressed bytes to read
     * @return the entry contents
     * @throws IOException if reading fails or the entry exceeds {@code maxBytes}
     */
    public static byte[] readEntry(ZipInputStream zis, long maxBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = zis.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new IOException("ZIP entry exceeds the maximum allowed size of %d bytes".formatted(maxBytes));
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }
}
