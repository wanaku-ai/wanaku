package ai.wanaku.core.config.provider.file;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class EncryptionHelperTest {

    private static final String PASSWORD = "test-password-123";
    private static final String SALT = "test-salt-1234567890";

    @Test
    void encryptDecryptRoundTrip() throws Exception {
        String original = "api.key=secret-value\npassword=secret-password";
        byte[] encrypted = EncryptionHelper.encrypt(original.getBytes(StandardCharsets.UTF_8), PASSWORD, SALT);
        byte[] decrypted = EncryptionHelper.decrypt(encrypted, PASSWORD, SALT);

        assertEquals(original, new String(decrypted, StandardCharsets.UTF_8));
    }

    @Test
    void encryptDecryptRoundTripEmptyPayload() throws Exception {
        byte[] original = new byte[0];

        byte[] encrypted = EncryptionHelper.encrypt(original, PASSWORD, SALT);
        byte[] decrypted = EncryptionHelper.decrypt(encrypted, PASSWORD, SALT);

        assertArrayEquals(original, decrypted);
    }

    @Test
    void encryptDecryptRoundTripVeryShortPayloads() throws Exception {
        byte[][] payloads = {
            new byte[] {0x00},
            new byte[] {0x01},
            new byte[] {(byte) 0xFF},
            new byte[] {0x00, 0x01},
            new byte[] {(byte) 0x80, 0x7F}
        };

        for (byte[] original : payloads) {
            byte[] encrypted = EncryptionHelper.encrypt(original, PASSWORD, SALT);
            byte[] decrypted = EncryptionHelper.decrypt(encrypted, PASSWORD, SALT);

            assertArrayEquals(original, decrypted);
        }
    }

    @Test
    void encryptDecryptRoundTripBinaryPayload() throws Exception {
        byte[] original =
                new byte[] {0x00, 0x01, 0x02, 0x03, (byte) 0xFF, (byte) 0x80, (byte) 0x7F, 0x10, 0x20, (byte) 0xFE};

        byte[] encrypted = EncryptionHelper.encrypt(original, PASSWORD, SALT);
        byte[] decrypted = EncryptionHelper.decrypt(encrypted, PASSWORD, SALT);

        assertArrayEquals(original, decrypted);
    }

    @Test
    void encryptedDataDiffersFromOriginal() throws Exception {
        String original = "secret data";
        byte[] encrypted = EncryptionHelper.encrypt(original.getBytes(StandardCharsets.UTF_8), PASSWORD, SALT);

        assertNotEquals(original, new String(encrypted, StandardCharsets.UTF_8));
    }

    @Test
    void differentIVsProduceDifferentCiphertext() throws Exception {
        byte[] data = "same data".getBytes(StandardCharsets.UTF_8);
        byte[] encrypted1 = EncryptionHelper.encrypt(data, PASSWORD, SALT);
        byte[] encrypted2 = EncryptionHelper.encrypt(data, PASSWORD, SALT);

        assertFalse(Arrays.equals(encrypted1, encrypted2));
    }

    @Test
    void wrongPasswordFails() throws Exception {
        byte[] encrypted = EncryptionHelper.encrypt("data".getBytes(StandardCharsets.UTF_8), PASSWORD, SALT);

        assertThrows(Exception.class, () -> EncryptionHelper.decrypt(encrypted, "wrong-password", SALT));
    }

    @Test
    void wrongSaltFails() throws Exception {
        byte[] encrypted = EncryptionHelper.encrypt("data".getBytes(StandardCharsets.UTF_8), PASSWORD, SALT);

        assertThrows(Exception.class, () -> EncryptionHelper.decrypt(encrypted, PASSWORD, "wrong-salt"));
    }

    @Test
    void decryptNullDataThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> EncryptionHelper.decrypt(null, PASSWORD, SALT));
    }

    @Test
    void decryptTooShortDataThrowsIllegalArgumentException() {
        byte[] tooShort = new byte[15]; // IV_LENGTH is 16
        assertThrows(IllegalArgumentException.class, () -> EncryptionHelper.decrypt(tooShort, PASSWORD, SALT));
    }
}
