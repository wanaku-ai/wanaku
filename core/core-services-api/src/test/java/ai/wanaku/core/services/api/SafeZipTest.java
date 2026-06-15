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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import ai.wanaku.capabilities.sdk.api.exceptions.WanakuException;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SafeZipTest {

    @Test
    void decodeArchiveAcceptsSmallPayload() throws Exception {
        byte[] data = "hello world".getBytes();
        assertArrayEquals(data, SafeZip.decodeArchive(Base64.getEncoder().encodeToString(data)));
    }

    @Test
    void decodeArchiveRejectsOversizedPayload() {
        // A Base64 string whose decoded length would exceed the cap is rejected before decoding.
        int tooLong = (int) (SafeZip.MAX_ARCHIVE_BYTES / 3 * 4) + 8;
        String b64 = "A".repeat(tooLong);
        assertThrows(WanakuException.class, () -> SafeZip.decodeArchive(b64));
    }

    @Test
    void decodeArchiveRejectsInvalidBase64() {
        assertThrows(WanakuException.class, () -> SafeZip.decodeArchive("not valid base64 !!!"));
    }

    @Test
    void decodeArchiveRejectsNull() {
        assertThrows(WanakuException.class, () -> SafeZip.decodeArchive(null));
    }

    @Test
    void readEntryReadsWithinLimit() throws Exception {
        byte[] payload = "0123456789".getBytes();
        try (ZipInputStream zis = zipOf("ok.txt", payload)) {
            zis.getNextEntry();
            assertArrayEquals(payload, SafeZip.readEntry(zis, 1024));
        }
    }

    @Test
    void readEntryRejectsEntryOverLimit() throws Exception {
        byte[] payload = "0123456789".getBytes(); // 10 bytes
        try (ZipInputStream zis = zipOf("big.txt", payload)) {
            zis.getNextEntry();
            assertThrows(IOException.class, () -> SafeZip.readEntry(zis, 4)); // limit < entry size
        }
    }

    private static ZipInputStream zipOf(String name, byte[] content) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            zos.putNextEntry(new ZipEntry(name));
            zos.write(content);
            zos.closeEntry();
        }
        return new ZipInputStream(new ByteArrayInputStream(bos.toByteArray()));
    }
}
